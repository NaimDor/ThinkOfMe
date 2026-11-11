import Core
import DesignSystem
import SwiftUI

@MainActor
@Observable
final class OccasionsModel {
    private let backend: any Backend
    private(set) var occasions: [Occasion] = []
    private(set) var people: [String: Person] = [:]
    private(set) var circles: [Circle] = []
    private(set) var holidayChoices: Set<String>?
    private(set) var loaded = false

    init(backend: any Backend) { self.backend = backend }

    var meId: String? { backend.currentUser?.id }
    var upcoming: [UpcomingOccasion] { Overview.upcoming(occasions, people: people, today: .now) }

    /// Holidays the user celebrates, with their next date, soonest first.
    var holidays: [(holiday: Holiday, date: Date)] {
        let ids = holidayChoices ?? Holidays.defaults(forLanguage: Fmt.language)
        return Holidays.all.filter { ids.contains($0.id) }
            .compactMap { h in h.nextDate(from: .now).map { (holiday: h, date: $0) } }
            .sorted { $0.date < $1.date }
    }

    func observe() async {
        let o = backend.occasions(), p = backend.people(), c = backend.circles(), h = backend.holidayChoices()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { @MainActor in for await value in h { self.holidayChoices = value } }
            group.addTask { @MainActor in for await value in o { self.occasions = value; self.loaded = true } }
            group.addTask { @MainActor in for await value in p { self.people = value } }
            group.addTask { @MainActor in for await value in c { self.circles = value } }
        }
    }

    func save(_ occasion: Occasion) async { _ = try? await backend.saveOccasion(occasion) }
    func delete(_ id: String) async { try? await backend.deleteOccasion(id) }
}

struct OccasionsView: View {
    @State var model: OccasionsModel
    var openPerson: (String) -> Void
    @State private var adding = false

    var body: some View {
        List {
            if model.loaded && model.upcoming.isEmpty {
                EmptyStateView(L("dates_empty_title"), message: L("dates_empty_body")) {
                    AppButton(L("add_date"), systemImage: "plus") { adding = true }.fixedSize()
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }
            ForEach(model.upcoming) { upcoming in
                Button { upcoming.person.map { openPerson($0.id) } } label: {
                    PersonRow(person: upcoming.person, title: Fmt.occasionTitle(upcoming, meId: model.meId)) {
                        Text(Fmt.shortDate(upcoming.date)).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                    } trailing: {
                        DaysChip(days: upcoming.daysUntil)
                    }
                }
                .buttonStyle(.plain)
                .listRowBackground(Color.clear)
                .listRowSeparatorTint(AppColor.hairline)
                .swipeActions {
                    if upcoming.occasion.ownerId == model.meId {
                        Button(L("action_delete"), role: .destructive) { Task { await model.delete(upcoming.occasion.id) } }
                    }
                }
            }
            if !model.holidays.isEmpty {
                Section {
                    ForEach(model.holidays, id: \.holiday.id) { item in
                        HStack(spacing: 14) {
                            Image(systemName: "gift")
                                .foregroundStyle(AppColor.accentText)
                                .frame(width: 44, height: 44)
                                .background(AppColor.surfaceTint, in: .circle)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(Fmt.holidayName(item.holiday)).textStyle(.bodyStrong).foregroundStyle(AppColor.ink)
                                Text(Fmt.shortDate(item.date)).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                            }
                            Spacer()
                            DaysChip(days: Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: .now), to: item.date).day ?? 0)
                        }
                        .padding(.vertical, Space.s)
                        .listRowBackground(Color.clear)
                        .listRowSeparatorTint(AppColor.hairline)
                    }
                } header: {
                    Text(L("settings_holidays")).textStyle(.title).foregroundStyle(AppColor.ink).textCase(nil)
                }
            }
            if model.upcoming.count >= 6 {
                InlineBanner().listRowBackground(Color.clear).listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .screenBackground()
        .navigationTitle(L("dates_title"))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { adding = true } label: { Image(systemName: "plus") }.accessibilityLabel(L("add_date"))
            }
        }
        .sheet(isPresented: $adding) {
            AddOccasionSheet(meId: model.meId ?? "", people: model.people, circles: model.circles) { occasion in
                adding = false
                Task { await model.save(occasion) }
            }
        }
        .task { await model.observe() }
    }
}

struct DaysChip: View {
    let days: Int
    var body: some View {
        let soon = days <= 14
        Text(Fmt.relativeDays(days))
            .textStyle(.caption).fontWeight(.semibold)
            .foregroundStyle(soon ? AppColor.accentText : AppColor.inkMuted)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(soon ? AppColor.surfaceTint : AppColor.surfaceMuted, in: Capsule())
    }
}

struct AddOccasionSheet: View {
    let meId: String
    let people: [String: Person]
    let circles: [Circle]
    var save: (Occasion) -> Void

    @State private var kind: OccasionKind = .birthday
    @State private var personId: String?
    @State private var title = ""
    @State private var date = Date.now
    @State private var yearly = true
    @State private var circleIds: [String] = [allCircles]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Space.m) {
                    FlowLayout(spacing: Space.xs) {
                        ForEach(OccasionKind.allCases, id: \.self) { k in
                            ChoiceChip(Fmt.kindTitle(k), isSelected: kind == k) { kind = k }
                        }
                    }
                    Text(L("date_whose")).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                    FlowLayout(spacing: Space.xs) {
                        ChoiceChip(L("me"), isSelected: (personId ?? meId) == meId) { personId = meId }
                        ForEach(people.values.filter { $0.id != meId }.sorted { $0.displayName < $1.displayName }) { person in
                            ChoiceChip(person.displayName, isSelected: personId == person.id) { personId = person.id }
                        }
                    }
                    if kind != .birthday { AppTextField(L("date_title"), text: $title) }
                    DatePicker(L("date_pick"), selection: $date, displayedComponents: .date)
                        .textStyle(.body).tint(AppColor.accent)
                    Toggle(L("date_yearly"), isOn: $yearly).textStyle(.body).tint(AppColor.accent)
                    Text(L("visible_to")).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                    FlowLayout(spacing: Space.xs) {
                        ChoiceChip(L("visible_all"), isSelected: circleIds.contains(allCircles)) {
                            circleIds = circleIds.contains(allCircles) ? [] : [allCircles]
                        }
                        ForEach(circles) { circle in
                            ChoiceChip(circle.name, isSelected: circleIds.contains(circle.id)) {
                                if circleIds.contains(circle.id) { circleIds.removeAll { $0 == circle.id } } else {
                                    circleIds.removeAll { $0 == allCircles }
                                    circleIds.append(circle.id)
                                }
                            }
                        }
                    }
                }
                .padding(Space.l)
            }
            .screenBackground()
            .safeAreaInset(edge: .bottom) {
                AppButton(L("action_save")) {
                    let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
                    save(Occasion(ownerId: meId, personId: personId ?? meId, kind: kind,
                                  title: kind == .birthday ? "Birthday" : title.trimmingCharacters(in: .whitespaces),
                                  month: parts.month ?? 1, day: parts.day ?? 1, year: parts.year, yearly: yearly,
                                  visibleCircleIds: circleIds))
                }
                .disabled(kind != .birthday && title.trimmingCharacters(in: .whitespaces).isEmpty)
                .padding(Space.l)
                .background(AppColor.canvas)
            }
            .navigationTitle(L("add_date"))
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
