import Foundation

/// Visibility entry meaning "every circle I am in, now and in the future".
public let allCircles = "*"

public struct SessionUser: Equatable, Sendable {
    public let id: String
    public let displayName: String
    public let email: String?
    public init(id: String, displayName: String, email: String?) {
        self.id = id; self.displayName = displayName; self.email = email
    }
}

public struct AboutMe: Equatable, Sendable, Codable {
    public var clothingSize: String?
    public var shoeSize: String?
    public var ringSize: String?
    public var favoriteColors: String?
    public var allergies: String?
    public var dontBuy: String?

    public init(clothingSize: String? = nil, shoeSize: String? = nil, ringSize: String? = nil,
                favoriteColors: String? = nil, allergies: String? = nil, dontBuy: String? = nil) {
        self.clothingSize = clothingSize; self.shoeSize = shoeSize; self.ringSize = ringSize
        self.favoriteColors = favoriteColors; self.allergies = allergies; self.dontBuy = dontBuy
    }

    public var isEmpty: Bool {
        [clothingSize, shoeSize, ringSize, favoriteColors, allergies, dontBuy].allSatisfy { ($0 ?? "").trimmingCharacters(in: .whitespaces).isEmpty }
    }
}

public struct Person: Identifiable, Equatable, Sendable {
    public let id: String
    public var displayName: String
    public var photoURL: URL?
    public var hueIndex: Int
    public var aboutMe: AboutMe

    public init(id: String, displayName: String, photoURL: URL? = nil, hueIndex: Int = 0, aboutMe: AboutMe = AboutMe()) {
        self.id = id; self.displayName = displayName; self.photoURL = photoURL; self.hueIndex = hueIndex; self.aboutMe = aboutMe
    }

    public var initial: String { displayName.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? "?" }
}

public enum CircleKind: String, Sendable { case pair, group }

public struct GiftCircle: Identifiable, Equatable, Sendable {
    public let id: String
    public var name: String
    public var kind: CircleKind
    public var ownerId: String
    public var memberIds: [String]
    public var adminIds: [String]
    public init(id: String, name: String, kind: CircleKind, ownerId: String, memberIds: [String], adminIds: [String]) {
        self.id = id; self.name = name; self.kind = kind; self.ownerId = ownerId; self.memberIds = memberIds; self.adminIds = adminIds
    }
}

public enum WishKind: String, Sendable { case product, idea }
public enum WishStatus: String, Sendable { case active, received, archived }

/// 1 = nice to have, 2 = would love, 3 = dream gift.
public enum Priority: Int, CaseIterable, Sendable { case nice = 1, love = 2, dream = 3 }

public struct Money: Equatable, Sendable {
    public var amount: Double
    public var currency: String
    public init(amount: Double, currency: String) { self.amount = amount; self.currency = currency }
}

public struct Wish: Identifiable, Equatable, Sendable {
    public let id: String
    public var ownerId: String
    public var title: String
    public var note: String?
    public var urls: [URL]
    public var imageURLs: [URL]
    public var price: Money?
    public var store: String?
    public var kind: WishKind
    public var priority: Priority
    public var quantity: Int
    public var status: WishStatus
    public var visibleCircleIds: [String]
    public var createdAt: Date

    public init(id: String, ownerId: String, title: String, note: String? = nil, urls: [URL] = [], imageURLs: [URL] = [],
                price: Money? = nil, store: String? = nil, kind: WishKind = .product, priority: Priority = .love,
                quantity: Int = 1, status: WishStatus = .active, visibleCircleIds: [String] = [], createdAt: Date = .now) {
        self.id = id; self.ownerId = ownerId; self.title = title; self.note = note; self.urls = urls; self.imageURLs = imageURLs
        self.price = price; self.store = store; self.kind = kind; self.priority = priority; self.quantity = quantity
        self.status = status; self.visibleCircleIds = visibleCircleIds; self.createdAt = createdAt
    }
}

/// What the user edits when adding or changing a wish.
public struct WishDraft: Equatable, Sendable {
    public var title = ""
    public var note = ""
    public var url = ""
    public var imageURLs: [URL] = []
    public var priceText = ""
    public var currency = Locale.current.currency?.identifier ?? "USD"
    public var store = ""
    public var kind: WishKind = .product
    public var priority: Priority = .love
    public var visibleCircleIds: [String] = [allCircles]

    public init() {}

    public init(wish: Wish) {
        title = wish.title; note = wish.note ?? ""; url = wish.urls.first?.absoluteString ?? ""
        imageURLs = wish.imageURLs; priceText = wish.price.map { Self.format($0.amount) } ?? ""
        currency = wish.price?.currency ?? currency; store = wish.store ?? ""; kind = wish.kind
        priority = wish.priority; visibleCircleIds = wish.visibleCircleIds
    }

    public var isValid: Bool {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && trimmed.count <= 140
    }

    public var price: Money? {
        Double(priceText.replacingOccurrences(of: ",", with: ".")).map { Money(amount: $0, currency: currency) }
    }

    public static func format(_ amount: Double) -> String {
        amount.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(amount)) : String(format: "%.2f", amount)
    }

    /// "All my circles" and specific circles are mutually exclusive.
    public mutating func toggleCircle(_ id: String) {
        if id == allCircles {
            visibleCircleIds = visibleCircleIds.contains(allCircles) ? [] : [allCircles]
        } else if visibleCircleIds.contains(id) {
            visibleCircleIds.removeAll { $0 == id }
        } else {
            visibleCircleIds.removeAll { $0 == allCircles }
            visibleCircleIds.append(id)
        }
    }
}

public enum ClaimStatus: String, Sendable { case reserved, purchased }

/// "I'm getting this". Never visible to the wish's owner.
public struct Claim: Equatable, Sendable {
    public let wishId: String
    public let wishOwnerId: String
    public let claimerId: String
    public let claimerName: String
    public var status: ClaimStatus
    public init(wishId: String, wishOwnerId: String, claimerId: String, claimerName: String, status: ClaimStatus = .reserved) {
        self.wishId = wishId; self.wishOwnerId = wishOwnerId; self.claimerId = claimerId; self.claimerName = claimerName; self.status = status
    }
}

public enum OccasionKind: String, CaseIterable, Sendable { case birthday, anniversary, holiday, custom }

public struct Occasion: Identifiable, Equatable, Sendable {
    public var id: String
    public var ownerId: String
    public var personId: String?
    public var kind: OccasionKind
    public var title: String
    public var month: Int
    public var day: Int
    public var year: Int?
    public var yearly: Bool
    public var visibleCircleIds: [String]

    public init(id: String = "", ownerId: String, personId: String?, kind: OccasionKind, title: String, month: Int, day: Int,
                year: Int? = nil, yearly: Bool = true, visibleCircleIds: [String] = [allCircles]) {
        self.id = id; self.ownerId = ownerId; self.personId = personId; self.kind = kind; self.title = title
        self.month = month; self.day = day; self.year = year; self.yearly = yearly; self.visibleCircleIds = visibleCircleIds
    }

    public var celebratedId: String { personId ?? ownerId }

    /// Next date the occasion lands on (29 Feb → 28 Feb in common years), or nil for a past one-off.
    public func nextDate(from today: Date, calendar: Calendar = .current) -> Date? {
        let start = calendar.startOfDay(for: today)
        func on(_ year: Int) -> Date? {
            var components = DateComponents(year: year, month: month, day: 1)
            guard let first = calendar.date(from: components),
                  let range = calendar.range(of: .day, in: .month, for: first) else { return nil }
            components.day = min(day, range.count)
            return calendar.date(from: components)
        }
        let thisYear = calendar.component(.year, from: start)
        if !yearly {
            guard let year, let date = on(year), date >= start else { return nil }
            return date
        }
        guard let candidate = on(thisYear) else { return nil }
        return candidate >= start ? candidate : on(thisYear + 1)
    }

    public func daysUntil(from today: Date, calendar: Calendar = .current) -> Int? {
        guard let next = nextDate(from: today, calendar: calendar) else { return nil }
        return calendar.dateComponents([.day], from: calendar.startOfDay(for: today), to: next).day
    }
}

public struct UpcomingOccasion: Identifiable, Equatable, Sendable {
    public var id: String { occasion.id }
    public let occasion: Occasion
    public let person: Person?
    public let date: Date
    public let daysUntil: Int
}

public struct PersonSummary: Identifiable, Equatable, Sendable {
    public var id: String { person.id }
    public let person: Person
    public let nextOccasion: UpcomingOccasion?
    public let newWishCount: Int
    public let wishCount: Int
}

public struct Invite: Sendable { public let code: String; public let url: URL; public let expiresAt: Date
    public init(code: String, url: URL, expiresAt: Date) { self.code = code; self.url = url; self.expiresAt = expiresAt } }

public struct InvitePreview: Sendable { public let circleName: String; public let kind: CircleKind; public let inviterName: String; public let memberCount: Int
    public init(circleName: String, kind: CircleKind, inviterName: String, memberCount: Int) {
        self.circleName = circleName; self.kind = kind; self.inviterName = inviterName; self.memberCount = memberCount } }

public struct LinkPreview: Sendable {
    public let url: URL; public let title: String?; public let imageURLs: [URL]; public let siteName: String?; public let price: Money?
    public init(url: URL, title: String?, imageURLs: [URL], siteName: String?, price: Money?) {
        self.url = url; self.title = title; self.imageURLs = imageURLs; self.siteName = siteName; self.price = price
    }
}

/// What could be read from a wish photo (product, price tag, store screenshot). Every field is optional.
public struct PhotoDetails: Equatable, Sendable {
    public var isProduct: Bool
    public var title: String?
    public var brand: String?
    public var model: String?
    public var store: String?
    public var price: Money?
    public var url: URL?
    public var note: String?
    public var searchQuery: String?

    public init(isProduct: Bool, title: String? = nil, brand: String? = nil, model: String? = nil, store: String? = nil,
                price: Money? = nil, url: URL? = nil, note: String? = nil, searchQuery: String? = nil) {
        self.isProduct = isProduct; self.title = title; self.brand = brand; self.model = model; self.store = store
        self.price = price; self.url = url; self.note = note; self.searchQuery = searchQuery
    }
}

extension WishDraft {
    /// Only spend a photo read when there is something left to fill in.
    public var needsDetails: Bool { title.isEmpty || priceText.isEmpty || store.isEmpty }

    /// Fills empty fields from what was read off a photo. Anything the user typed wins.
    public func merging(_ details: PhotoDetails) -> WishDraft {
        var draft = self
        let fallbackTitle = [details.brand, details.model].compactMap { $0 }.joined(separator: " ")
        if draft.title.isEmpty { draft.title = details.title ?? fallbackTitle }
        if draft.priceText.isEmpty, let price = details.price {
            draft.priceText = WishDraft.format(price.amount)
            draft.currency = price.currency
        }
        if draft.store.isEmpty { draft.store = details.store ?? "" }
        if draft.url.isEmpty { draft.url = details.url?.absoluteString ?? "" }
        if draft.note.isEmpty { draft.note = details.note ?? "" }
        return draft
    }
}

/// The values a photo read put into a draft (nil = that field was not filled), plus the currency
/// it replaced. Clearing only removes values the user has not changed since.
public struct PhotoFill: Equatable, Sendable {
    public var title: String?
    public var priceText: String?
    public var currency: String?
    public var previousCurrency: String?
    public var store: String?
    public var url: String?
    public var note: String?

    public var isEmpty: Bool { [title, priceText, store, url, note].allSatisfy { $0 == nil } }

    /// What `merged` filled in compared with `before`, or nil if nothing changed.
    public init?(before: WishDraft, merged: WishDraft) {
        func filled(_ old: String, _ new: String) -> String? { old.isEmpty && !new.isEmpty ? new : nil }
        title = filled(before.title, merged.title)
        priceText = filled(before.priceText, merged.priceText)
        currency = priceText != nil ? merged.currency : nil
        previousCurrency = priceText != nil ? before.currency : nil
        store = filled(before.store, merged.store)
        url = filled(before.url, merged.url)
        note = filled(before.note, merged.note)
        if isEmpty { return nil }
    }
}

extension WishDraft {
    /// Empties each field still holding what the photo put there.
    public func clearing(_ fill: PhotoFill) -> WishDraft {
        var draft = self
        func clear(_ current: String, _ filled: String?) -> String { filled != nil && current == filled ? "" : current }
        let priceCleared = fill.priceText != nil && draft.priceText == fill.priceText
        draft.title = clear(draft.title, fill.title)
        draft.priceText = clear(draft.priceText, fill.priceText)
        if priceCleared, draft.currency == fill.currency, let previous = fill.previousCurrency { draft.currency = previous }
        draft.store = clear(draft.store, fill.store)
        draft.url = clear(draft.url, fill.url)
        draft.note = clear(draft.note, fill.note)
        return draft
    }
}

