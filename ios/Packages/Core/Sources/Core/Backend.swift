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
    /// Holidays the user turned on, or nil if they never chose (language defaults apply).
    func holidayChoices() -> AsyncStream<Set<String>?>
    func setHolidays(_ ids: Set<String>) async throws
    func remindersEnabled() -> AsyncStream<Bool>
    func setRemindersEnabled(_ enabled: Bool) async throws
    func saveDevice(timeZone: String, locale: String, pushToken: String?) async throws

    // Circles
    func circles() -> AsyncStream<[GiftCircle]>
    func createCircle(name: String, kind: CircleKind) async throws -> String
    func createInvite(circleId: String) async throws -> Invite
    func previewInvite(code: String) async throws -> InvitePreview
    func joinWithInvite(code: String) async throws -> String
    func leaveCircle(_ circleId: String) async throws
    /// Admins only.
    func renameCircle(_ circleId: String, name: String) async throws
    /// Admins only; the owner can't be removed.
    func removeMember(_ userId: String, from circleId: String) async throws

    // Wishes
    func myWishes() -> AsyncStream<[Wish]>
    func visibleWishes() -> AsyncStream<[Wish]>
    func wishes(of personId: String) -> AsyncStream<[Wish]>
    func wish(_ id: String) -> AsyncStream<Wish?>
    /// A fresh id for a wish about to be created (its photos are stored under it).
    func newWishId() -> String
    func saveWish(id: String, isNew: Bool, draft: WishDraft) async throws -> String
    /// Uploads a JPEG for a wish and returns its download URL.
    func uploadPhoto(_ jpeg: Data, wishId: String) async throws -> URL
    func markReceived(_ wishId: String) async throws
    func deleteWish(_ wishId: String) async throws
    /// Reads a product page. With a wishId, its photos are copied into that wish's storage.
    func unfurl(_ url: URL, wishId: String?) async throws -> LinkPreview
    /// Reads product name, store, price and more from a JPEG photo.
    func analyzePhoto(_ jpeg: Data, locale: String) async throws -> PhotoDetails

    // Claims (never returned for your own wishes)
    func claims(onWishesOf personId: String) -> AsyncStream<[String: [Claim]]>
    func myClaims() -> AsyncStream<[Claim]>
    func claim(_ wish: Wish) async throws
    func release(_ wishId: String) async throws
    /// "Planning to buy" → "bought" (and back).
    func setPurchased(_ wishId: String, purchased: Bool) async throws

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
