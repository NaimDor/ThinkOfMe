import Foundation

/// In-memory sample world for builds without Firebase, previews and tests.
/// It mirrors the backend's visibility rules, including the surprise
/// guarantee: claims on your own wishes are never returned.
@MainActor
public final class DemoBackend: Backend {
    public let isDemo = true
    public let meId = "me"
    private let hub = Broadcaster()

    private var user: SessionUser? { didSet { hub.notify() } }
    public private(set) var peopleById: [String: Person] = [:]
    public private(set) var circleList: [GiftCircle] = []
    public private(set) var wishList: [Wish] = []
    public private(set) var claimList: [Claim] = []
    public private(set) var occasionList: [Occasion] = []
    private var reminders = true
    private var holidays: Set<String>?

    public var currentUser: SessionUser? { user }

    public init(signedIn: Bool = false, today: Date = .now) {
        seed(today: today)
        if signedIn { user = SessionUser(id: meId, displayName: "Dana", email: nil) }
    }

    private func seed(today: Date) {
        let calendar = Calendar.current
        func occasion(_ id: String, _ person: String, _ kind: OccasionKind, _ title: String, inDays days: Int, age: Int?, circles: [String]) -> Occasion {
            let date = calendar.date(byAdding: .day, value: days, to: today)!
            let parts = calendar.dateComponents([.year, .month, .day], from: date)
            return Occasion(id: id, ownerId: person, personId: person, kind: kind, title: title, month: parts.month!, day: parts.day!,
                            year: age.map { parts.year! - $0 }, yearly: true, visibleCircleIds: circles)
        }
        func ago(_ days: Double) -> Date { today.addingTimeInterval(-days * 86_400) }

        peopleById = [
            meId: Person(id: meId, displayName: "Dana", hueIndex: 2, aboutMe: AboutMe(clothingSize: "M", shoeSize: "39", favoriteColors: "Forest green, cream")),
            "maya": Person(id: "maya", displayName: "Maya", hueIndex: 0, aboutMe: AboutMe(clothingSize: "S", shoeSize: "38", ringSize: "6", favoriteColors: "Black, terracotta", dontBuy: "Candles, please, I have enough")),
            "mom": Person(id: "mom", displayName: "Mom", hueIndex: 1, aboutMe: AboutMe(allergies: "Lilies")),
            "dan": Person(id: "dan", displayName: "Dan", hueIndex: 2),
            "noa": Person(id: "noa", displayName: "Noa", hueIndex: 3),
        ]
        circleList = [
            GiftCircle(id: "us", name: "Maya & me", kind: .pair, ownerId: meId, memberIds: [meId, "maya"], adminIds: [meId]),
            GiftCircle(id: "family", name: "Family", kind: .group, ownerId: "mom", memberIds: [meId, "maya", "mom", "dan", "noa"], adminIds: ["mom", meId]),
        ]
        occasionList = [
            occasion("o1", "maya", .birthday, "Birthday", inDays: 12, age: 34, circles: ["us", "family"]),
            occasion("o2", "mom", .anniversary, "Mom & Dad's anniversary", inDays: 21, age: 38, circles: ["family"]),
            occasion("o3", "dan", .birthday, "Birthday", inDays: 37, age: nil, circles: ["family"]),
            occasion("o4", "noa", .birthday, "Birthday", inDays: 95, age: nil, circles: ["family"]),
            occasion("o5", meId, .birthday, "Birthday", inDays: 64, age: 33, circles: [allCircles]),
        ]
        let shop = URL(string: "https://example.com/headphones")!
        wishList = [
            Wish(id: "w1", ownerId: "maya", title: "Noise-cancelling headphones", note: "Matte black please, for flights and deep-work days.", urls: [shop], price: Money(amount: 349, currency: "USD"), store: "Soundhouse", visibleCircleIds: ["us", "family"], createdAt: ago(2)),
            Wish(id: "w2", ownerId: "maya", title: "Ceramics class for two", note: "Any weekend works. Let's go together!", kind: .idea, priority: .dream, visibleCircleIds: ["us"], createdAt: ago(1)),
            Wish(id: "w3", ownerId: "maya", title: "Italian cookbook", price: Money(amount: 32, currency: "USD"), store: "Bookshop", priority: .nice, visibleCircleIds: ["us", "family"], createdAt: ago(30)),
            Wish(id: "w4", ownerId: "maya", title: "Silk scarf, terracotta", price: Money(amount: 85, currency: "USD"), store: "Atelier", visibleCircleIds: ["family", "us"], createdAt: ago(40)),
            Wish(id: "w5", ownerId: "maya", title: "Running socks (3 pairs)", price: Money(amount: 18, currency: "USD"), priority: .nice, quantity: 3, visibleCircleIds: ["family"], createdAt: ago(50)),
            Wish(id: "w6", ownerId: "mom", title: "Large monstera plant", note: "In a ceramic pot if possible.", price: Money(amount: 65, currency: "USD"), store: "Green House", visibleCircleIds: ["family"], createdAt: ago(20)),
            Wish(id: "w7", ownerId: "dan", title: "Instant film camera", price: Money(amount: 129, currency: "USD"), store: "PhotoLab", priority: .dream, visibleCircleIds: ["family"], createdAt: ago(3)),
            Wish(id: "w8", ownerId: "noa", title: "Climbing gym pass", kind: .idea, visibleCircleIds: ["family"], createdAt: ago(60)),
            Wish(id: "w9", ownerId: meId, title: "Linen bedding set", note: "Sage green, queen size.", price: Money(amount: 180, currency: "USD"), store: "Linen Co.", visibleCircleIds: [allCircles], createdAt: ago(5)),
            Wish(id: "w10", ownerId: meId, title: "Pour-over coffee kit", price: Money(amount: 45, currency: "USD"), priority: .nice, visibleCircleIds: [allCircles], createdAt: ago(9)),
            Wish(id: "w11", ownerId: meId, title: "Weekend in the mountains", note: "Anywhere with a fireplace.", kind: .idea, priority: .dream, visibleCircleIds: ["us"], createdAt: ago(14)),
        ]
        // Noa is getting Maya's headphones; Maya is getting Dana's coffee kit (Dana must never see it).
        claimList = [
            Claim(wishId: "w1", wishOwnerId: "maya", claimerId: "noa", claimerName: "Noa"),
            Claim(wishId: "w10", wishOwnerId: meId, claimerId: "maya", claimerName: "Maya", status: .purchased),
        ]
    }

    public func viewers(ownerId: String, circleIds: [String]) -> Set<String> {
        let shared = circleList.filter { (circleIds.contains(allCircles) || circleIds.contains($0.id)) && $0.memberIds.contains(ownerId) }
        return Set(shared.flatMap(\.memberIds)).subtracting([ownerId])
    }

    private func visible() -> [Wish] {
        wishList.filter { $0.ownerId != meId && viewers(ownerId: $0.ownerId, circleIds: $0.visibleCircleIds).contains(meId) }
    }

    private func changed() { hub.notify() }

    // MARK: Account

    public func session() -> AsyncStream<SessionUser?> { hub.stream { [unowned self] in user } }
    public func signInWithGoogle(idToken: String, accessToken: String) async throws { try await signInWithDemoData() }
    public func signInWithApple(idToken: String, rawNonce: String, fullName: PersonNameComponents?) async throws { try await signInWithDemoData() }
    public func signInWithDemoData() async throws {
        try await Task.sleep(for: .milliseconds(250))
        user = SessionUser(id: meId, displayName: "Dana", email: nil)
    }
    public func signOut() throws { user = nil }
    public func deleteAccount() async throws { user = nil }

    // MARK: Profiles

    public func me() -> AsyncStream<Person?> { hub.stream { [unowned self] in peopleById[meId] } }
    public func people() -> AsyncStream<[String: Person]> { hub.stream { [unowned self] in peopleById } }
    public func saveProfile(displayName: String, aboutMe: AboutMe) async throws {
        peopleById[meId]?.displayName = displayName
        peopleById[meId]?.aboutMe = aboutMe
        changed()
    }
    public func holidayChoices() -> AsyncStream<Set<String>?> { hub.stream { [unowned self] in holidays } }
    public func setHolidays(_ ids: Set<String>) async throws { holidays = ids; changed() }
    public func remindersEnabled() -> AsyncStream<Bool> { hub.stream { [unowned self] in reminders } }
    public func setRemindersEnabled(_ enabled: Bool) async throws { reminders = enabled; changed() }
    public func saveDevice(timeZone: String, locale: String, pushToken: String?) async throws {}

    // MARK: Circles

    public func circles() -> AsyncStream<[GiftCircle]> { hub.stream { [unowned self] in circleList.filter { $0.memberIds.contains(meId) } } }
    public func createCircle(name: String, kind: CircleKind) async throws -> String {
        let id = UUID().uuidString
        circleList.append(GiftCircle(id: id, name: name, kind: kind, ownerId: meId, memberIds: [meId], adminIds: [meId]))
        changed()
        return id
    }
    public func createInvite(circleId: String) async throws -> Invite {
        try await Task.sleep(for: .milliseconds(300))
        return Invite(code: "H7K2PQ9M", url: URL(string: "https://thinkofme.web.app/i/H7K2PQ9M")!, expiresAt: .now.addingTimeInterval(7 * 86_400))
    }
    public func previewInvite(code: String) async throws -> InvitePreview {
        InvitePreview(circleName: "Friends", kind: .group, inviterName: "Noa", memberCount: 4)
    }
    public func joinWithInvite(code: String) async throws -> String { try await createCircle(name: "Friends", kind: .group) }
    public func leaveCircle(_ circleId: String) async throws { circleList.removeAll { $0.id == circleId }; changed() }
    public func renameCircle(_ circleId: String, name: String) async throws {
        if let i = circleList.firstIndex(where: { $0.id == circleId }) { circleList[i].name = name; changed() }
    }
    public func removeMember(_ userId: String, from circleId: String) async throws {
        guard let i = circleList.firstIndex(where: { $0.id == circleId }), circleList[i].ownerId != userId else { return }
        circleList[i].memberIds.removeAll { $0 == userId }
        circleList[i].adminIds.removeAll { $0 == userId }
        changed()
    }

    // MARK: Wishes

    public func myWishes() -> AsyncStream<[Wish]> {
        hub.stream { [unowned self] in wishList.filter { $0.ownerId == meId }.sorted { $0.createdAt > $1.createdAt } }
    }
    public func visibleWishes() -> AsyncStream<[Wish]> { hub.stream { [unowned self] in visible() } }
    public func wishes(of personId: String) -> AsyncStream<[Wish]> {
        personId == meId ? myWishes() : hub.stream { [unowned self] in visible().filter { $0.ownerId == personId }.sorted { $0.createdAt > $1.createdAt } }
    }
    public func wish(_ id: String) -> AsyncStream<Wish?> { hub.stream { [unowned self] in wishList.first { $0.id == id } } }

    public func newWishId() -> String { UUID().uuidString }

    /// Demo mode keeps photos on the device.
    public func uploadPhoto(_ jpeg: Data, wishId: String) async throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(wishId)-\(UUID().uuidString).jpg")
        try jpeg.write(to: url)
        return url
    }

    public func saveWish(id: String, isNew: Bool, draft: WishDraft) async throws -> String {
        let wishId = id
        let existing = wishList.first { $0.id == wishId }
        let wish = Wish(
            id: wishId, ownerId: meId, title: draft.title.trimmingCharacters(in: .whitespacesAndNewlines),
            note: draft.note.isEmpty ? nil : draft.note, urls: URL(string: draft.url).map { [$0] } ?? [],
            imageURLs: draft.imageURLs, price: draft.price, store: draft.store.isEmpty ? nil : draft.store,
            kind: draft.kind, priority: draft.priority, status: existing?.status ?? .active,
            visibleCircleIds: draft.visibleCircleIds, createdAt: existing?.createdAt ?? .now
        )
        wishList.removeAll { $0.id == wishId }
        wishList.append(wish)
        changed()
        return wishId
    }
    public func markReceived(_ wishId: String) async throws {
        if let index = wishList.firstIndex(where: { $0.id == wishId }) { wishList[index].status = .received }
        changed()
    }
    public func deleteWish(_ wishId: String) async throws {
        wishList.removeAll { $0.id == wishId }
        claimList.removeAll { $0.wishId == wishId }
        changed()
    }
    public func unfurl(_ url: URL) async throws -> LinkPreview {
        try await Task.sleep(for: .milliseconds(700))
        return LinkPreview(url: url, title: nil, imageURLs: [], siteName: url.host()?.replacingOccurrences(of: "www.", with: ""), price: nil)
    }

    public func analyzePhoto(_ jpeg: Data, locale: String) async throws -> PhotoDetails {
        try await Task.sleep(for: .milliseconds(1200))
        return PhotoDetails(isProduct: true, title: "Ceramic pour-over coffee set", brand: "Hario", store: "Kitchen & Co.",
                            price: Money(amount: 189, currency: "ILS"), note: "White, 2-cup size",
                            searchQuery: "Hario ceramic pour-over set white 2 cup")
    }

    // MARK: Claims

    public func claims(onWishesOf personId: String) -> AsyncStream<[String: [Claim]]> {
        hub.stream { [unowned self] in
            // The surprise guarantee: never reveal claims on your own wishes.
            personId == meId ? [:] : Dictionary(grouping: claimList.filter { $0.wishOwnerId == personId }, by: \.wishId)
        }
    }
    public func myClaims() -> AsyncStream<[Claim]> { hub.stream { [unowned self] in claimList.filter { $0.claimerId == meId } } }
    public func claim(_ wish: Wish) async throws {
        guard wish.ownerId != meId else { throw DemoError.ownWish }
        claimList.removeAll { $0.wishId == wish.id && $0.claimerId == meId }
        claimList.append(Claim(wishId: wish.id, wishOwnerId: wish.ownerId, claimerId: meId, claimerName: peopleById[meId]?.displayName ?? ""))
        changed()
    }
    public func release(_ wishId: String) async throws {
        claimList.removeAll { $0.wishId == wishId && $0.claimerId == meId }
        changed()
    }
    public func setPurchased(_ wishId: String, purchased: Bool) async throws {
        if let i = claimList.firstIndex(where: { $0.wishId == wishId && $0.claimerId == meId }) {
            claimList[i].status = purchased ? .purchased : .reserved
            changed()
        }
    }

    // MARK: Occasions

    public func occasions() -> AsyncStream<[Occasion]> {
        hub.stream { [unowned self] in
            occasionList.filter { $0.ownerId == meId || viewers(ownerId: $0.ownerId, circleIds: $0.visibleCircleIds).contains(meId) }
        }
    }
    public func saveOccasion(_ occasion: Occasion) async throws -> String {
        var saved = occasion
        if saved.id.isEmpty { saved.id = UUID().uuidString }
        saved.ownerId = meId
        occasionList.removeAll { $0.id == saved.id }
        occasionList.append(saved)
        changed()
        return saved.id
    }
    public func deleteOccasion(_ id: String) async throws { occasionList.removeAll { $0.id == id }; changed() }

    enum DemoError: Error { case ownWish }
}
