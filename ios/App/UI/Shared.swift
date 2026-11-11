import Core
import DesignSystem
import SwiftUI

// MARK: - Localization

/// Looks up a key in Localizable.xcstrings (generated from the Android strings).
func L(_ key: String) -> String { NSLocalizedString(key, comment: "") }

/// Formats a localized string; plural rules apply to integer arguments.
func L(_ key: String, _ args: any CVarArg...) -> String {
    String(format: NSLocalizedString(key, comment: ""), locale: .current, arguments: args)
}

// MARK: - Formatting

enum Fmt {
    static func money(_ money: Money) -> String {
        money.amount.formatted(.currency(code: money.currency).precision(.fractionLength(money.amount.truncatingRemainder(dividingBy: 1) == 0 ? 0 : 2)))
    }

    static func longDate(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.wide).day().month(.wide))
    }

    static func shortDate(_ date: Date) -> String {
        date.formatted(.dateTime.day().month(.abbreviated))
    }

    static func mediumDate(_ date: Date) -> String {
        date.formatted(date: .abbreviated, time: .omitted)
    }

    static func relativeDays(_ days: Int) -> String {
        switch days {
        case 0: L("today")
        case 1: L("tomorrow")
        default: L("days_to_go", days)
        }
    }

    static func greeting(_ now: Date = .now) -> String {
        switch Calendar.current.component(.hour, from: now) {
        case 5...11: L("greeting_morning")
        case 12...17: L("greeting_afternoon")
        default: L("greeting_evening")
        }
    }

    static func priority(_ priority: Priority) -> String {
        switch priority {
        case .nice: L("priority_nice")
        case .love: L("priority_love")
        case .dream: L("priority_dream")
        }
    }

    static func kindName(_ kind: OccasionKind) -> String { L("kind_\(kind.rawValue)") }
    static func kindTitle(_ kind: OccasionKind) -> String { L("kind_\(kind.rawValue)_title") }

    /// "Maya's birthday", or the title its creator wrote for other occasions.
    static func occasionTitle(_ upcoming: UpcomingOccasion, meId: String?) -> String {
        let occasion = upcoming.occasion
        if occasion.kind != .birthday, !occasion.title.isEmpty { return occasion.title }
        let name = upcoming.person.flatMap { $0.id == meId ? nil : $0.displayName } ?? L("me")
        return L("occasion_possessive", name, kindName(occasion.kind))
    }
}

// MARK: - Views

struct PersonAvatar: View {
    let person: Person?
    var size: CGFloat = 46

    var body: some View {
        if let url = person?.photoURL {
            AsyncImage(url: url) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                AppColor.surfaceMuted
            }
            .frame(width: size, height: size)
            .clipShape(.circle)
        } else {
            InitialAvatar(initial: person?.initial ?? "?", hueIndex: person?.hueIndex ?? 0, size: size)
        }
    }
}

/// A wish's photo, or a calm tinted tile with a symbol when there is none.
struct WishImage: View {
    let wish: Wish
    var symbolSize: CGFloat = 40

    var body: some View {
        ZStack {
            AppColor.surfaceMuted
            Image(systemName: wish.kind == .idea ? "sparkle" : "gift")
                .font(.system(size: symbolSize, weight: .light))
                .foregroundStyle(AppColor.inkMuted.opacity(0.6))
            if let url = wish.imageURLs.first {
                AsyncImage(url: url) { phase in
                    if let image = phase.image { image.resizable().scaledToFill() }
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Radius.l, style: .continuous))
    }
}

/// Grid tile. `wrapped` must only be true when the viewer is not the owner.
struct WishCard: View {
    let wish: Wish
    var wrapped = false

    var body: some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            WishImage(wish: wish)
                .aspectRatio(1, contentMode: .fit)
                .overlay(alignment: .topLeading) {
                    HStack(spacing: 6) {
                        if wrapped { Tag(L("wrapped"), gold: true) }
                        if wish.kind == .idea { Tag(L("idea")) }
                    }
                    .padding(Space.xs)
                }
            VStack(alignment: .leading, spacing: 2) {
                Text(wish.title).textStyle(.callout).fontWeight(.semibold).foregroundStyle(AppColor.ink).lineLimit(2)
                Text(wish.price.map(Fmt.money) ?? Fmt.priority(wish.priority))
                    .textStyle(.caption).foregroundStyle(AppColor.inkMuted).lineLimit(1)
            }
            .padding(.horizontal, 2)
        }
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}

struct AboutMeCard: View {
    let about: AboutMe

    var body: some View {
        if !about.isEmpty {
            let rows: [(String, String?)] = [
                ("about_size_clothing", about.clothingSize), ("about_size_shoes", about.shoeSize), ("about_size_ring", about.ringSize),
                ("about_colors", about.favoriteColors), ("about_allergies", about.allergies), ("about_dont_buy", about.dontBuy),
            ]
            VStack(alignment: .leading, spacing: Space.xs) {
                Text(L("about_me").uppercased()).textStyle(.overline).foregroundStyle(AppColor.accentText)
                ForEach(rows.filter { !($0.1 ?? "").isEmpty }, id: \.0) { key, value in
                    HStack(alignment: .top, spacing: Space.s) {
                        Text(L(key)).textStyle(.callout).foregroundStyle(AppColor.inkMuted).frame(maxWidth: .infinity, alignment: .leading)
                        Text(value ?? "").textStyle(.callout).foregroundStyle(AppColor.ink).frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding(Space.m)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppColor.surface, in: RoundedRectangle(cornerRadius: Radius.l, style: .continuous))
        }
    }
}

/// A list row with a leading avatar, title, subtitle and trailing detail.
struct PersonRow<Subtitle: View, Trailing: View>: View {
    let person: Person?
    let title: String
    @ViewBuilder var subtitle: Subtitle
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: 14) {
            PersonAvatar(person: person)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).textStyle(.bodyStrong).foregroundStyle(AppColor.ink).lineLimit(1)
                HStack(spacing: 6) { subtitle }
            }
            Spacer(minLength: Space.s)
            trailing
        }
        .padding(.vertical, Space.s)
        .contentShape(.rect)
    }
}

extension View {
    /// Screen background used everywhere.
    func screenBackground() -> some View {
        background(AppColor.canvas.ignoresSafeArea())
    }
}
