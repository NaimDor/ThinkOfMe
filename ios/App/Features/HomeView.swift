import Core
import DesignSystem
import SwiftUI

@MainActor
@Observable
final class HomeModel {
    private let backend: any Backend
    private(set) var me: Person?
    private(set) var people: [String: Person] = [:]
    private(set) var visible: [Wish] = []
    private(set) var occasions: [Occasion] = []
    private(set) var claimedIds: Set<String> = []
    private(set) var loaded = false

    init(backend: any Backend) { self.backend = backend }

    var overview: Overview {
        Overview.build(me: me, people: people, visible: visible, occasions: occasions)
    }

    var nextUpOpen: Int { max(0, overview.nextUpWishCount - claimedIds.count) }

    func observe() async {
        let meStream = backend.me(), peopleStream = backend.people()
        let wishStream = backend.visibleWishes(), occasionStream = backend.occasions()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { @MainActor in for await value in meStream { self.me = value } }
            group.addTask { @MainActor in for await value in peopleStream { self.people = value; self.loaded = true } }
            group.addTask { @MainActor in for await value in wishStream { self.visible = value } }
            group.addTask { @MainActor in for await value in occasionStream { self.occasions = value } }
        }
    }

    /// Claims on the next-up person's wishes, to show how many are still open.
    func observeClaims(of personId: String?) async {
        guard let personId else { claimedIds = []; return }
        for await claims in backend.claims(onWishesOf: personId) { claimedIds = Set(claims.keys) }
    }
}

struct HomeView: View {
    @State var model: HomeModel
    var openPerson: (String) -> Void
    var openSettings: () -> Void
    var createCircle: () -> Void
    var isDemo: Bool

    var body: some View {
        let overview = model.overview
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if isDemo {
                    Text(L("demo_banner")).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                        .padding(.horizontal, Space.s).padding(.vertical, 6)
                        .background(AppColor.surfaceMuted, in: RoundedRectangle(cornerRadius: Radius.s))
                        .padding(.bottom, Space.s)
                }
                header(overview)
                    .padding(.bottom, 28)

                if let next = overview.nextUp {
                    NextUpCard(
                        title: Fmt.occasionTitle(next, meId: overview.me?.id),
                        date: Fmt.longDate(next.date),
                        days: next.daysUntil,
                        open: model.nextUpOpen,
                        total: overview.nextUpWishCount,
                        onView: { next.person.map { openPerson($0.id) } }
                    )
                    .padding(.bottom, Space.xl)
                    .transition(.opacity.combined(with: .offset(y: 8)))
                }

                if model.loaded && overview.people.isEmpty {
                    EmptyStateView(L("home_empty_title"), message: L("home_empty_body")) {
                        AppButton(L("home_empty_action"), systemImage: "plus", action: createCircle).fixedSize()
                    }
                } else {
                    SectionHeader(L("your_people"))
                    ForEach(Array(overview.people.enumerated()), id: \.element.id) { index, summary in
                        Button { openPerson(summary.person.id) } label: { row(summary) }
                            .buttonStyle(.plain)
                        if index < overview.people.count - 1 { Hairline() }
                        if index == 5 { NativeAdCard().padding(.vertical, Space.xs) }
                    }
                }
            }
            .padding(.horizontal, Space.l)
            .padding(.top, Space.s)
            .padding(.bottom, Space.xxl)
            .animation(.appSpring, value: overview.people.map(\.id))
        }
        .scrollIndicators(.hidden)
        .screenBackground()
        .task { await model.observe() }
        .task(id: overview.nextUp?.person?.id) { await model.observeClaims(of: overview.nextUp?.person?.id) }
    }

    private func header(_ overview: Overview) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                Text(Fmt.longDate(.now)).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                Text(Fmt.greeting()).textStyle(.displayLarge).foregroundStyle(AppColor.ink)
            }
            Spacer()
            Button(action: openSettings) { PersonAvatar(person: overview.me, size: 44) }
                .buttonStyle(PressableStyle())
                .accessibilityLabel(L("settings_title"))
        }
    }

    private func row(_ summary: PersonSummary) -> some View {
        PersonRow(person: summary.person, title: summary.person.displayName) {
            if summary.newWishCount > 0 {
                Circle().fill(AppColor.accent).frame(width: 7, height: 7)
                Text(L("new_wishes", summary.newWishCount)).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
            } else if let next = summary.nextOccasion {
                Text(Fmt.kindTitle(next.occasion.kind)).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
            }
        } trailing: {
            if let next = summary.nextOccasion {
                Text(Fmt.shortDate(next.date)).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
            }
        }
    }
}

struct NextUpCard: View {
    let title: String
    let date: String
    let days: Int
    let open: Int
    let total: Int
    let onView: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .center, spacing: Space.m) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(L("next_up").uppercased()).textStyle(.overline).foregroundStyle(AppColor.accentText)
                    Text(title).textStyle(.displayMedium).foregroundStyle(AppColor.ink)
                    Text(date).textStyle(.callout).foregroundStyle(AppColor.inkMuted)
                }
                Spacer(minLength: 0)
                CountdownRing(days: days, label: L("days_label"))
                    .accessibilityLabel(L("days_to_go", days))
            }
            HStack {
                if total > 0 {
                    Text(L("open_of_total", open, total)).textStyle(.callout).foregroundStyle(AppColor.ink)
                }
                Spacer()
                PillButton(L("action_view_list"), action: onView)
            }
        }
        .padding(22)
        .background(AppColor.surfaceTint, in: RoundedRectangle(cornerRadius: Radius.l, style: .continuous))
    }
}
