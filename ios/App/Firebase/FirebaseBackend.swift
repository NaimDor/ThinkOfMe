import Core
import DesignSystem
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import Foundation

/// Production backend on Firebase. Mirrors Android's Firebase repositories and
/// relies on the security rules in backend/firestore.rules (claims on your own
/// wishes are never queried; the rules would deny it anyway).
@MainActor
final class FirebaseBackend: Backend {
    let isDemo = false
    private let db = Firestore.firestore()
    private let functions = Functions.functions(region: "europe-west1")

    var currentUser: SessionUser? { Auth.auth().currentUser.map(Self.sessionUser) }
    private var uid: String? { Auth.auth().currentUser?.uid }

    private static func sessionUser(_ user: User) -> SessionUser {
        SessionUser(id: user.uid, displayName: user.displayName ?? "", email: user.email)
    }

    enum BackendError: Error { case notSignedIn, ownWish, badResponse }

    private func requireUid() throws -> String {
        guard let uid else { throw BackendError.notSignedIn }
        return uid
    }

    // MARK: Streams

    /// Turns a Firestore query or document listener into an AsyncStream.
    private func listen<T: Sendable>(_ query: Query, map: @escaping @Sendable ([QueryDocumentSnapshot]) -> T) -> AsyncStream<T> {
        let (stream, continuation) = AsyncStream<T>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let registration = query.addSnapshotListener { snapshot, _ in
            guard let snapshot else { return }
            continuation.yield(map(snapshot.documents))
        }
        continuation.onTermination = { _ in registration.remove() }
        return stream
    }

    private func listen<T: Sendable>(_ ref: DocumentReference, map: @escaping @Sendable (DocumentSnapshot) -> T) -> AsyncStream<T> {
        let (stream, continuation) = AsyncStream<T>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let registration = ref.addSnapshotListener { snapshot, _ in
            guard let snapshot else { return }
            continuation.yield(map(snapshot))
        }
        continuation.onTermination = { _ in registration.remove() }
        return stream
    }

    private func empty<T: Sendable>(_ value: T) -> AsyncStream<T> {
        AsyncStream { $0.yield(value) }
    }

    // MARK: Account

    func session() -> AsyncStream<SessionUser?> {
        let (stream, continuation) = AsyncStream<SessionUser?>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let handle = Auth.auth().addStateDidChangeListener { _, user in
            continuation.yield(user.map(Self.sessionUser))
        }
        continuation.onTermination = { _ in
            Task { @MainActor in Auth.auth().removeStateDidChangeListener(handle) }
        }
        return stream
    }

    func signInWithGoogle(idToken: String, accessToken: String) async throws {
        _ = try await Auth.auth().signIn(with: GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken))
    }

    func signInWithApple(idToken: String, rawNonce: String, fullName: PersonNameComponents?) async throws {
        let credential = OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: rawNonce, fullName: fullName)
        let result = try await Auth.auth().signIn(with: credential)
        // Apple only shares the name on first sign-in; keep it for onboarding.
        if let fullName, result.user.displayName == nil {
            let change = result.user.createProfileChangeRequest()
            change.displayName = PersonNameComponentsFormatter().string(from: fullName)
            try? await change.commitChanges()
        }
    }

    func signInWithDemoData() async throws {}

    func signOut() throws { try Auth.auth().signOut() }

    func deleteAccount() async throws {
        _ = try await functions.httpsCallable("deleteAccount").call()
        try? Auth.auth().signOut()
    }

    // MARK: Mapping

    nonisolated static func person(_ doc: DocumentSnapshot) -> Person {
        let about = doc.get("aboutMe") as? [String: Any] ?? [:]
        return Person(
            id: doc.documentID,
            displayName: doc.get("displayName") as? String ?? "",
            photoURL: (doc.get("photoUrl") as? String).flatMap(URL.init(string:)),
            hueIndex: doc.get("hue") as? Int ?? 0,
            aboutMe: AboutMe(
                clothingSize: about["clothingSize"] as? String, shoeSize: about["shoeSize"] as? String,
                ringSize: about["ringSize"] as? String, favoriteColors: about["favoriteColors"] as? String,
                allergies: about["allergies"] as? String, dontBuy: about["dontBuy"] as? String
            )
        )
    }

    nonisolated static func circle(_ doc: DocumentSnapshot) -> Circle {
        Circle(
            id: doc.documentID,
            name: doc.get("name") as? String ?? "",
            kind: CircleKind(rawValue: doc.get("kind") as? String ?? "") ?? .group,
            ownerId: doc.get("ownerId") as? String ?? "",
            memberIds: doc.get("memberIds") as? [String] ?? [],
            adminIds: doc.get("adminIds") as? [String] ?? []
        )
    }

    nonisolated static func wish(_ doc: DocumentSnapshot) -> Wish {
        let price = doc.get("price") as? [String: Any]
        return Wish(
            id: doc.documentID,
            ownerId: doc.get("ownerId") as? String ?? "",
            title: doc.get("title") as? String ?? "",
            note: doc.get("note") as? String,
            urls: (doc.get("urls") as? [String] ?? []).compactMap(URL.init(string:)),
            imageURLs: (doc.get("imageUrls") as? [String] ?? []).compactMap(URL.init(string:)),
            price: price.flatMap { p in
                guard let amount = (p["amount"] as? NSNumber)?.doubleValue, let currency = p["currency"] as? String else { return nil }
                return Money(amount: amount, currency: currency)
            },
            store: doc.get("store") as? String,
            kind: WishKind(rawValue: doc.get("kind") as? String ?? "") ?? .product,
            priority: Priority(rawValue: doc.get("priority") as? Int ?? 2) ?? .love,
            quantity: doc.get("quantity") as? Int ?? 1,
            status: WishStatus(rawValue: doc.get("status") as? String ?? "") ?? .active,
            visibleCircleIds: doc.get("visibleCircleIds") as? [String] ?? [],
            createdAt: (doc.get("createdAt") as? Timestamp)?.dateValue() ?? .now
        )
    }

    nonisolated static func claim(_ doc: DocumentSnapshot) -> Claim {
        Claim(
            wishId: doc.get("wishId") as? String ?? "",
            wishOwnerId: doc.get("wishOwnerId") as? String ?? "",
            claimerId: doc.get("claimerId") as? String ?? "",
            claimerName: doc.get("claimerName") as? String ?? "",
            status: ClaimStatus(rawValue: doc.get("status") as? String ?? "") ?? .reserved
        )
    }

    nonisolated static func occasion(_ doc: DocumentSnapshot) -> Occasion {
        Occasion(
            id: doc.documentID,
            ownerId: doc.get("ownerId") as? String ?? "",
            personId: doc.get("personId") as? String,
            kind: OccasionKind(rawValue: doc.get("kind") as? String ?? "") ?? .custom,
            title: doc.get("title") as? String ?? "",
            month: doc.get("month") as? Int ?? 1,
            day: doc.get("day") as? Int ?? 1,
            year: doc.get("year") as? Int,
            yearly: (doc.get("recurrence") as? String) != "once",
            visibleCircleIds: doc.get("visibleCircleIds") as? [String] ?? []
        )
    }

    // MARK: Profiles

    func me() -> AsyncStream<Person?> {
        guard let uid else { return empty(nil) }
        return listen(db.collection("users").document(uid)) { $0.exists ? Self.person($0) : nil }
    }

    func people() -> AsyncStream<[String: Person]> {
        guard let uid else { return empty([:]) }
        let (stream, continuation) = AsyncStream<[String: Person]>.makeStream(bufferingPolicy: .bufferingNewest(1))
        var profileListeners: [ListenerRegistration] = []
        var results: [Int: [String: Person]] = [:]
        // Follow the server-maintained connection list, then the profiles of those people (in chunks of 30).
        let connections = db.collection("connections").document(uid).addSnapshotListener { [db] snapshot, _ in
            profileListeners.forEach { $0.remove() }
            profileListeners = []
            results = [:]
            let ids = Array(Set((snapshot?.get("userIds") as? [String] ?? []) + [uid]))
            let chunks = stride(from: 0, to: ids.count, by: 30).map { Array(ids[$0..<min($0 + 30, ids.count)]) }
            for (index, chunk) in chunks.enumerated() {
                let listener = db.collection("users").whereField(FieldPath.documentID(), in: chunk).addSnapshotListener { snap, _ in
                    guard let snap else { return }
                    results[index] = Dictionary(uniqueKeysWithValues: snap.documents.map { ($0.documentID, Self.person($0)) })
                    continuation.yield(results.values.reduce(into: [:]) { $0.merge($1) { a, _ in a } })
                }
                profileListeners.append(listener)
            }
        }
        continuation.onTermination = { _ in
            connections.remove()
            Task { @MainActor in profileListeners.forEach { $0.remove() } }
        }
        return stream
    }

    func saveProfile(displayName: String, aboutMe: AboutMe) async throws {
        let uid = try requireUid()
        let about: [String: String] = [
            "clothingSize": aboutMe.clothingSize, "shoeSize": aboutMe.shoeSize, "ringSize": aboutMe.ringSize,
            "favoriteColors": aboutMe.favoriteColors, "allergies": aboutMe.allergies, "dontBuy": aboutMe.dontBuy,
        ].compactMapValues { value in value.flatMap { $0.isEmpty ? nil : $0 } }
        let ref = db.collection("users").document(uid)
        var data: [String: Any] = ["displayName": displayName, "aboutMe": about, "updatedAt": FieldValue.serverTimestamp()]
        if try await ref.getDocument().exists {
            try await ref.updateData(data)
        } else {
            data["createdAt"] = FieldValue.serverTimestamp()
            data["hue"] = PersonHue.index(for: uid)
            try await ref.setData(data)
        }
    }

    func remindersEnabled() -> AsyncStream<Bool> {
        guard let uid else { return empty(true) }
        return listen(db.collection("users").document(uid).collection("private").document("settings")) { snapshot in
            ((snapshot.get("notifications") as? [String: Any])?["reminders"] as? Bool) ?? true
        }
    }

    func setRemindersEnabled(_ enabled: Bool) async throws {
        let uid = try requireUid()
        try await db.collection("users").document(uid).collection("private").document("settings")
            .setData(["notifications": ["reminders": enabled]], merge: true)
    }

    func saveDevice(timeZone: String, locale: String, pushToken: String?) async throws {
        guard let uid else { return }
        let user = db.collection("users").document(uid)
        try await user.collection("private").document("settings").setData(["timeZone": timeZone, "locale": locale], merge: true)
        if let pushToken {
            try await user.collection("devices").document(String(pushToken.suffix(40)))
                .setData(["token": pushToken, "platform": "ios", "locale": locale, "updatedAt": FieldValue.serverTimestamp()])
        }
    }

    // MARK: Circles

    func circles() -> AsyncStream<[Circle]> {
        guard let uid else { return empty([]) }
        return listen(db.collection("circles").whereField("memberIds", arrayContains: uid)) { docs in
            docs.map(Self.circle).sorted { $0.name < $1.name }
        }
    }

    func createCircle(name: String, kind: CircleKind) async throws -> String {
        let uid = try requireUid()
        let ref = db.collection("circles").document()
        try await ref.setData([
            "name": name, "kind": kind.rawValue, "ownerId": uid, "memberIds": [uid], "adminIds": [uid],
            "createdAt": FieldValue.serverTimestamp(), "updatedAt": FieldValue.serverTimestamp(),
        ])
        return ref.documentID
    }

    private func call(_ name: String, _ data: [String: Any]) async throws -> [String: Any] {
        let result = try await functions.httpsCallable(name).call(data)
        return result.data as? [String: Any] ?? [:]
    }

    func createInvite(circleId: String) async throws -> Invite {
        let result = try await call("createInvite", ["circleId": circleId])
        guard let code = result["code"] as? String, let url = (result["url"] as? String).flatMap(URL.init(string:)) else {
            throw BackendError.badResponse
        }
        let expires = (result["expiresAt"] as? NSNumber)?.doubleValue ?? 0
        return Invite(code: code, url: url, expiresAt: Date(timeIntervalSince1970: expires / 1000))
    }

    func previewInvite(code: String) async throws -> InvitePreview {
        let result = try await call("previewInvite", ["code": code])
        return InvitePreview(
            circleName: result["circleName"] as? String ?? "",
            kind: CircleKind(rawValue: result["kind"] as? String ?? "") ?? .group,
            inviterName: result["inviterName"] as? String ?? "",
            memberCount: (result["memberCount"] as? NSNumber)?.intValue ?? 0
        )
    }

    func joinWithInvite(code: String) async throws -> String {
        guard let id = try await call("redeemInvite", ["code": code])["circleId"] as? String else { throw BackendError.badResponse }
        return id
    }

    func leaveCircle(_ circleId: String) async throws {
        _ = try await call("leaveCircle", ["circleId": circleId])
    }

    // MARK: Wishes

    func myWishes() -> AsyncStream<[Wish]> {
        guard let uid else { return empty([]) }
        return listen(db.collection("wishes").whereField("ownerId", isEqualTo: uid).order(by: "sortKey", descending: true)) { $0.map(Self.wish) }
    }

    func visibleWishes() -> AsyncStream<[Wish]> {
        guard let uid else { return empty([]) }
        return listen(db.collection("wishes").whereField("viewerIds", arrayContains: uid).order(by: "updatedAt", descending: true).limit(to: 300)) {
            $0.map(Self.wish)
        }
    }

    func wishes(of personId: String) -> AsyncStream<[Wish]> {
        guard let uid else { return empty([]) }
        if personId == uid { return myWishes() }
        return listen(
            db.collection("wishes").whereField("viewerIds", arrayContains: uid).whereField("ownerId", isEqualTo: personId)
                .order(by: "sortKey", descending: true)
        ) { $0.map(Self.wish) }
    }

    func wish(_ id: String) -> AsyncStream<Wish?> {
        listen(db.collection("wishes").document(id)) { $0.exists ? Self.wish($0) : nil }
    }

    func saveWish(id: String?, draft: WishDraft) async throws -> String {
        let uid = try requireUid()
        let ref = id.map { db.collection("wishes").document($0) } ?? db.collection("wishes").document()
        var data: [String: Any] = [
            "ownerId": uid,
            "title": draft.title.trimmingCharacters(in: .whitespacesAndNewlines),
            "note": draft.note.isEmpty ? NSNull() : draft.note,
            "urls": draft.url.isEmpty ? [] : [draft.url],
            "imageUrls": draft.imageURLs.prefix(8).map(\.absoluteString),
            "price": draft.price.map { ["amount": $0.amount, "currency": $0.currency] as [String: Any] } ?? NSNull(),
            "store": draft.store.isEmpty ? NSNull() : draft.store,
            "kind": draft.kind.rawValue,
            "priority": draft.priority.rawValue,
            "quantity": 1,
            "visibleCircleIds": draft.visibleCircleIds,
            "updatedAt": FieldValue.serverTimestamp(),
        ]
        if id == nil {
            data["status"] = "active"
            data["viewerIds"] = [String]()
            data["sortKey"] = Int(Date.now.timeIntervalSince1970 * 1000)
            data["createdAt"] = FieldValue.serverTimestamp()
            try await ref.setData(data)
        } else {
            try await ref.updateData(data)
        }
        return ref.documentID
    }

    func markReceived(_ wishId: String) async throws {
        try await db.collection("wishes").document(wishId).updateData(["status": "received", "updatedAt": FieldValue.serverTimestamp()])
    }

    func deleteWish(_ wishId: String) async throws {
        try await db.collection("wishes").document(wishId).delete()
    }

    func unfurl(_ url: URL) async throws -> LinkPreview {
        let data = try await call("unfurlLink", ["url": url.absoluteString])
        let price = data["price"] as? [String: Any]
        return LinkPreview(
            url: (data["url"] as? String).flatMap(URL.init(string:)) ?? url,
            title: data["title"] as? String,
            imageURLs: (data["imageUrls"] as? [String] ?? []).compactMap(URL.init(string:)),
            siteName: data["siteName"] as? String,
            price: price.flatMap { p in
                guard let amount = (p["amount"] as? NSNumber)?.doubleValue, let currency = p["currency"] as? String else { return nil }
                return Money(amount: amount, currency: currency)
            }
        )
    }

    // MARK: Claims

    func claims(onWishesOf personId: String) -> AsyncStream<[String: [Claim]]> {
        // Never query claims on your own wishes.
        guard let uid, personId != uid else { return empty([:]) }
        return listen(db.collection("claims").whereField("viewerIds", arrayContains: uid).whereField("wishOwnerId", isEqualTo: personId)) { docs in
            Dictionary(grouping: docs.map(Self.claim), by: \.wishId)
        }
    }

    func myClaims() -> AsyncStream<[Claim]> {
        guard let uid else { return empty([]) }
        return listen(db.collection("claims").whereField("claimerId", isEqualTo: uid)) { $0.map(Self.claim) }
    }

    func claim(_ wish: Wish) async throws {
        let uid = try requireUid()
        guard wish.ownerId != uid else { throw BackendError.ownWish }
        let wishDoc = try await db.collection("wishes").document(wish.id).getDocument()
        let viewers = wishDoc.get("viewerIds") as? [String] ?? []
        let myName = try await db.collection("users").document(uid).getDocument().get("displayName") as? String ?? ""
        try await db.collection("claims").document("\(wish.id)_\(uid)").setData([
            "wishId": wish.id, "wishOwnerId": wish.ownerId, "claimerId": uid, "claimerName": myName,
            "viewerIds": viewers, "status": "reserved", "quantity": 1,
            "createdAt": FieldValue.serverTimestamp(), "updatedAt": FieldValue.serverTimestamp(),
        ])
    }

    func release(_ wishId: String) async throws {
        let uid = try requireUid()
        try await db.collection("claims").document("\(wishId)_\(uid)").delete()
    }

    // MARK: Occasions

    func occasions() -> AsyncStream<[Occasion]> {
        guard let uid else { return empty([]) }
        let mine = listen(db.collection("occasions").whereField("ownerId", isEqualTo: uid)) { $0.map(Self.occasion) }
        let shared = listen(db.collection("occasions").whereField("viewerIds", arrayContains: uid)) { $0.map(Self.occasion) }
        let (stream, continuation) = AsyncStream<[Occasion]>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let task = Task { @MainActor in
            var latest: (mine: [Occasion], shared: [Occasion]) = ([], [])
            func emit() {
                var seen = Set<String>()
                continuation.yield((latest.mine + latest.shared).filter { seen.insert($0.id).inserted })
            }
            await withTaskGroup(of: Void.self) { group in
                group.addTask { @MainActor in for await value in mine { latest.mine = value; emit() } }
                group.addTask { @MainActor in for await value in shared { latest.shared = value; emit() } }
            }
        }
        continuation.onTermination = { _ in task.cancel() }
        return stream
    }

    func saveOccasion(_ occasion: Occasion) async throws -> String {
        let uid = try requireUid()
        let ref = occasion.id.isEmpty ? db.collection("occasions").document() : db.collection("occasions").document(occasion.id)
        var data: [String: Any] = [
            "ownerId": uid,
            "personId": occasion.personId ?? uid,
            "kind": occasion.kind.rawValue,
            "title": occasion.title,
            "month": occasion.month,
            "day": occasion.day,
            "year": occasion.year.map { $0 as Any } ?? NSNull(),
            "recurrence": occasion.yearly ? "yearly" : "once",
            "visibleCircleIds": occasion.visibleCircleIds,
        ]
        if occasion.id.isEmpty {
            data["viewerIds"] = [String]()
            try await ref.setData(data)
        } else {
            try await ref.updateData(data)
        }
        return ref.documentID
    }

    func deleteOccasion(_ id: String) async throws {
        try await db.collection("occasions").document(id).delete()
    }
}
