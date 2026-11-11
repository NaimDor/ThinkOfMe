import Foundation

/// The app's data contract. `FirebaseBackend` (app target) talks to the real
/// backend; `DemoBackend` is an in-memory world used when the app is built
/// without a Firebase config, and in previews and tests.
///
/// Observations are `AsyncStream`s that emit the current value immediately
/// and again on every change.
@MainActor
public protocol Backend: AnyObject {
    var isDemo: Bool { get }
    var currentUser: SessionUser? { get }
    func session() -> AsyncStream<SessionUser?>

    // Account
    func signInWithGoogle(idToken: String, accessToken: String) async throws
    func signInWithApple(idToken: String, rawNonce: String, fullName: PersonNameComponents?) async throws
    func signInWithDemoData() async throws
    func signOut() throws
    func deleteAccount() async throws

    // Profiles
    func me() -> AsyncStream<Person?>
    func people() -> AsyncStream<[String: Person]>
    func saveProfile(displayName: String, aboutMe: AboutMe) async throws
    func remindersEnabled() -> AsyncStream<Bool>
    func setRemindersEnabled(_ enabled: Bool) async throws
    func saveDevice(timeZone: String, locale: String, pushToken: String?) async throws

    // Circles
    func circles() -> AsyncStream<[Circle]>
    func createCircle(name: String, kind: CircleKind) async throws -> String
    func createInvite(circleId: String) async throws -> Invite
    func previewInvite(code: String) async throws -> InvitePreview
    func joinWithInvite(code: String) async throws -> String
    func leaveCircle(_ circleId: String) async throws

    // Wishes
    func myWishes() -> AsyncStream<[Wish]>
    func visibleWishes() -> AsyncStream<[Wish]>
    func wishes(of personId: String) -> AsyncStream<[Wish]>
    func wish(_ id: String) -> AsyncStream<Wish?>
    func saveWish(id: String?, draft: WishDraft) async throws -> String
    func markReceived(_ wishId: String) async throws
    func deleteWish(_ wishId: String) async throws
    func unfurl(_ url: URL) async throws -> LinkPreview

    // Claims (never returned for your own wishes)
    func claims(onWishesOf personId: String) -> AsyncStream<[String: [Claim]]>
    func myClaims() -> AsyncStream<[Claim]>
    func claim(_ wish: Wish) async throws
    func release(_ wishId: String) async throws

    // Occasions
    func occasions() -> AsyncStream<[Occasion]>
    func saveOccasion(_ occasion: Occasion) async throws -> String
    func deleteOccasion(_ id: String) async throws
}

/// Re-emits values to many subscribers when `notify()` is called.
@MainActor
public final class Broadcaster {
    private var subscribers: [UUID: @MainActor () -> Void] = [:]

    public init() {}

    public func stream<T: Sendable>(_ value: @escaping @MainActor () -> T) -> AsyncStream<T> {
        let (stream, continuation) = AsyncStream<T>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let id = UUID()
        continuation.yield(value())
        subscribers[id] = { continuation.yield(value()) }
        continuation.onTermination = { [weak self] _ in
            Task { @MainActor in self?.subscribers[id] = nil }
        }
        return stream
    }

    public func notify() {
        for emit in subscribers.values { emit() }
    }
}
