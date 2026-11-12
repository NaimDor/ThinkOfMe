import Core
import DesignSystem
import FirebaseAppCheck
import FirebaseCore
import FirebaseCrashlytics
import FirebaseMessaging
import GoogleSignIn
import SwiftUI
import UserNotifications

@main
struct ThinkOfMeApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var app: AppModel
    private let services: AppServices

    init() {
        AppFonts.register()
        let firebase = AppServices.configureFirebase()
        let backend: any Backend = firebase ? FirebaseBackend() : DemoBackend()
        _app = State(initialValue: AppModel(backend: backend))
        services = AppServices(usesFirebase: firebase)
    }

    var body: some Scene {
        WindowGroup {
            RootView(app: app, services: services)
                .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
                .task { services.ads.gatherConsent() }
                .environment(services.ads)
        }
    }
}

/// App-wide services that sit outside the data layer.
@MainActor
final class AppServices {
    let usesFirebase: Bool
    let ads = AdsController()

    init(usesFirebase: Bool) { self.usesFirebase = usesFirebase }

    /// Firebase is optional: without GoogleService-Info.plist the app runs on demo data.
    static func configureFirebase() -> Bool {
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return false }
        // App Check proves requests come from this app. Debug builds print a token to register in the console.
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(DeviceCheckProviderFactory())
        #endif
        FirebaseApp.configure()
        return true
    }

    /// Google sign-in, available when Firebase provides a client id.
    var googleSignIn: (() async throws -> (idToken: String, accessToken: String))? {
        guard usesFirebase, let clientID = FirebaseApp.app()?.options.clientID else { return nil }
        return {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
            guard let root = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first else {
                throw CancellationError()
            }
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: root)
            guard let idToken = result.user.idToken?.tokenString else { throw CancellationError() }
            return (idToken, result.user.accessToken.tokenString)
        }
    }

    var privacyOptions: (() -> Void)? {
        ads.privacyOptionsRequired ? { [ads] in ads.showPrivacyOptions() } : nil
    }

    /// Ask for notification permission and tell the backend our time zone, language and push token.
    func registerDevice(_ backend: any Backend) async {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        if granted { UIApplication.shared.registerForRemoteNotifications() }
        var token: String?
        if usesFirebase { token = try? await Messaging.messaging().token() }
        try? await backend.saveDevice(timeZone: TimeZone.current.identifier, locale: Locale.current.identifier, pushToken: token)
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        if FirebaseApp.app() != nil { Messaging.messaging().apnsToken = deviceToken }
    }

    /// Show reminders even while the app is open.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    /// A tapped notification opens the screen it is about.
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        guard let link = IncomingLink.fromPush(info) else { return }
        await MainActor.run { PushRouter.shared.pending = link }
    }
}
