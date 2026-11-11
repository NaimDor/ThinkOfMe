import Foundation

/// Everything the Home screen shows, derived from raw data. Pure, so it is
/// shared by the app, previews and tests (mirrors Android's ObserveOverview).
public struct Overview: Equatable, Sendable {
    public let me: Person?
    public let nextUp: UpcomingOccasion?
    public let nextUpWishCount: Int
    public let people: [PersonSummary]
    public let upcoming: [UpcomingOccasion]

    public static let newWishWindow: TimeInterval = 14 * 86_400

    public static func upcoming(_ occasions: [Occasion], people: [String: Person], today: Date, calendar: Calendar = .current) -> [UpcomingOccasion] {
        occasions.compactMap { occasion in
            guard let date = occasion.nextDate(from: today, calendar: calendar),
                  let days = occasion.daysUntil(from: today, calendar: calendar) else { return nil }
            return UpcomingOccasion(occasion: occasion, person: people[occasion.celebratedId], date: date, daysUntil: days)
        }
        .sorted { $0.daysUntil < $1.daysUntil }
    }

    public static func build(me: Person?, people: [String: Person], visible: [Wish], occasions: [Occasion], today: Date = .now, calendar: Calendar = .current) -> Overview {
        let upcoming = upcoming(occasions, people: people, today: today, calendar: calendar)
        let active = visible.filter { $0.status == .active }
        let summaries = people.values
            .filter { $0.id != me?.id }
            .map { person -> PersonSummary in
                let theirs = active.filter { $0.ownerId == person.id }
                return PersonSummary(
                    person: person,
                    nextOccasion: upcoming.first { $0.occasion.celebratedId == person.id },
                    newWishCount: theirs.filter { today.timeIntervalSince($0.createdAt) < newWishWindow }.count,
                    wishCount: theirs.count
                )
            }
            .sorted { a, b in
                let da = a.nextOccasion?.daysUntil ?? .max, db = b.nextOccasion?.daysUntil ?? .max
                return da == db ? a.person.displayName < b.person.displayName : da < db
            }
        let nextUp = upcoming.first { $0.person?.id != me?.id && $0.person != nil }
        let nextUpWishes = nextUp.map { next in active.filter { $0.ownerId == next.person?.id }.count } ?? 0
        return Overview(me: me, nextUp: nextUp, nextUpWishCount: nextUpWishes, people: summaries, upcoming: upcoming)
    }
}
