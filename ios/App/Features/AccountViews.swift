import AuthenticationServices
import Core
import CryptoKit
import DesignSystem
import SwiftUI

// MARK: - Sign in

struct SignInView: View {
    let backend: any Backend
    var googleSignIn: (() async throws -> (idToken: String, accessToken: String))?
    @State private var busy = false
    @State private var failed = false
    @State private var nonce = ""

    var body: some View {
        VStack(spacing: Space.l) {
            Spacer()
            GiftBoxMoment().frame(width: 180, height: 180)
            VStack(spacing: Space.s) {
                Text(L("welcome_title")).textStyle(.displayLarge).foregroundStyle(AppColor.ink)
                Text(L("welcome_body")).textStyle(.body).foregroundStyle(AppColor.inkMuted)
            }
            .multilineTextAlignment(.center)
            Spacer()
            if failed { Text(L("error_generic")).textStyle(.caption).foregroundStyle(AppColor.danger) }
            if backend.isDemo {
                AppButton(L("explore_demo"), systemImage: "gift", isLoading: busy) { run { try await backend.signInWithDemoData() } }
            } else {
                SignInWithAppleButton(.continue) { request in
                    nonce = Self.randomNonce()
                    request.requestedScopes = [.fullName, .email]
                    request.nonce = Self.sha256(nonce)
                } onCompletion: { result in
                    guard case .success(let auth) = result,
                          let credential = auth.credential as? ASAuthorizationAppleIDCredential,
                          let token = credential.identityToken.flatMap({ String(data: $0, encoding: .utf8) }) else { return }
                    run { try await backend.signInWithApple(idToken: token, rawNonce: nonce, fullName: credential.fullName) }
                }
                .signInWithAppleButtonStyle(.black)
                .frame(height: 56)
                .clipShape(RoundedRectangle(cornerRadius: Radius.m, style: .continuous))
                if let googleSignIn {
                    AppButton(L("continue_google"), kind: .secondary, isLoading: busy) {
                        run {
                            let tokens = try await googleSignIn()
                            try await backend.signInWithGoogle(idToken: tokens.idToken, accessToken: tokens.accessToken)
                        }
                    }
                }
            }
        }
        .padding(Space.l)
        .screenBackground()
    }

    private func run(_ action: @escaping @MainActor () async throws -> Void) {
        Task {
            busy = true
            failed = false
            defer { busy = false }
            do { try await action() } catch {
                // Cancelling the system sheet is not an error worth showing.
                if (error as? ASAuthorizationError)?.code != .canceled { failed = true }
            }
        }
    }

    static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var generator = SystemRandomNumberGenerator()
        return String((0..<length).map { _ in charset[Int(generator.next() % UInt64(charset.count))] })
    }

    static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

// MARK: - Onboarding

struct OnboardingView: View {
    let backend: any Backend
    var done: () -> Void
    @State private var name: String
    @State private var birthday = Date.now
    @State private var hasBirthday = false
    @State private var saving = false
    @State private var failed = false

    init(backend: any Backend, done: @escaping () -> Void) {
        self.backend = backend
        self.done = done
        _name = State(initialValue: backend.currentUser?.displayName ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Space.m) {
            Text(L("onboarding_name")).textStyle(.displayMedium).foregroundStyle(AppColor.ink)
            AppTextField(L("settings_name"), text: $name).textContentType(.givenName)
            Text(L("onboarding_birthday")).textStyle(.displayMedium).foregroundStyle(AppColor.ink).padding(.top, Space.s)
            Text(L("onboarding_birthday_hint")).textStyle(.callout).foregroundStyle(AppColor.inkMuted)
            DatePicker(L("date_pick"), selection: $birthday, in: ...Date.now, displayedComponents: .date)
                .tint(AppColor.accent)
                .onChange(of: birthday) { hasBirthday = true }
            Spacer()
            if failed { Text(L("error_generic")).textStyle(.caption).foregroundStyle(AppColor.danger) }
            AppButton(L("onboarding_continue"), isLoading: saving) { Task { await finish() } }
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .padding(Space.l)
        .padding(.top, Space.xl)
        .screenBackground()
    }

    private func finish() async {
        saving = true
        defer { saving = false }
        do {
            try await backend.saveProfile(displayName: name.trimmingCharacters(in: .whitespaces), aboutMe: AboutMe())
            if hasBirthday, let me = backend.currentUser?.id {
                let parts = Calendar.current.dateComponents([.year, .month, .day], from: birthday)
                _ = try await backend.saveOccasion(Occasion(ownerId: me, personId: me, kind: .birthday, title: "Birthday",
                                                            month: parts.month ?? 1, day: parts.day ?? 1, year: parts.year))
            }
            done()
        } catch {
            failed = true
        }
    }
}

// MARK: - Settings

struct SettingsView: View {
    let backend: any Backend
    var privacyOptions: (() -> Void)?
    @State private var name = ""
    @State private var about = AboutMe()
    @State private var reminders = true
    @State private var holidays: Set<String> = []
    @State private var saved = false
    @State private var confirmDelete = false

    var body: some View {
        Form {
            Section(L("settings_profile")) {
                TextField(L("settings_name"), text: $name)
                TextField(L("about_size_clothing"), text: binding(\.clothingSize))
                TextField(L("about_size_shoes"), text: binding(\.shoeSize))
                TextField(L("about_size_ring"), text: binding(\.ringSize))
                TextField(L("about_colors"), text: binding(\.favoriteColors))
                TextField(L("about_allergies"), text: binding(\.allergies))
                TextField(L("about_dont_buy"), text: binding(\.dontBuy), axis: .vertical)
                Button(saved ? L("action_done") : L("action_save")) {
                    Task {
                        try? await backend.saveProfile(displayName: name, aboutMe: about)
                        saved = true
                    }
                }
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            Section {
                Toggle(isOn: $reminders) {
                    VStack(alignment: .leading) {
                        Text(L("settings_notifications"))
                        Text(L("settings_notifications_body")).font(.footnote).foregroundStyle(AppColor.inkMuted)
                    }
                }
                .tint(AppColor.accent)
                .onChange(of: reminders) { _, value in Task { try? await backend.setRemindersEnabled(value) } }
            }
            Section {
                ForEach(Holidays.all.sorted { ($0.nextDate(from: .now) ?? .distantFuture) < ($1.nextDate(from: .now) ?? .distantFuture) }) { holiday in
                    Toggle(isOn: Binding(
                        get: { holidays.contains(holiday.id) },
                        set: { on in
                            if on { holidays.insert(holiday.id) } else { holidays.remove(holiday.id) }
                            let chosen = holidays
                            Task { try? await backend.setHolidays(chosen) }
                        }
                    )) {
                        VStack(alignment: .leading) {
                            Text(Fmt.holidayName(holiday))
                            if let next = holiday.nextDate(from: .now) {
                                Text(Fmt.shortDate(next)).font(.footnote).foregroundStyle(AppColor.inkMuted)
                            }
                        }
                    }
                    .tint(AppColor.accent)
                    .disabled(!reminders)
                }
            } header: {
                Text(L("settings_holidays"))
            } footer: {
                Text(L("settings_holidays_body"))
            }
            Section {
                if let privacyOptions { Button(L("settings_privacy_ads"), action: privacyOptions) }
                Button(L("settings_sign_out")) { try? backend.signOut() }
                Button(L("settings_delete_account"), role: .destructive) { confirmDelete = true }
            }
        }
        .scrollContentBackground(.hidden)
        .screenBackground()
        .tint(AppColor.accentText)
        .navigationTitle(L("settings_title"))
        .confirmationDialog(L("settings_delete_account"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(L("action_delete"), role: .destructive) { Task { try? await backend.deleteAccount() } }
        } message: { Text(L("delete_account_body")) }
        .task {
            var meIterator = backend.me().makeAsyncIterator()
            if let me = await meIterator.next() ?? nil { name = me.displayName; about = me.aboutMe }
            var remindersIterator = backend.remindersEnabled().makeAsyncIterator()
            reminders = await remindersIterator.next() ?? true
            var holidayIterator = backend.holidayChoices().makeAsyncIterator()
            holidays = (await holidayIterator.next() ?? nil) ?? Holidays.defaults(forLanguage: Fmt.language)
        }
        .onChange(of: name) { saved = false }
        .onChange(of: about) { saved = false }
    }

    private func binding(_ keyPath: WritableKeyPath<AboutMe, String?>) -> Binding<String> {
        Binding(get: { about[keyPath: keyPath] ?? "" }, set: { about[keyPath: keyPath] = $0.isEmpty ? nil : $0 })
    }
}
