import Core
import DesignSystem
import SwiftUI

// MARK: - Person

@MainActor
@Observable
final class PersonModel {
    private let backend: any Backend
    let personId: String
    private(set) var person: Person?
    private(set) var wishes: [Wish] = []
    private(set) var claimed: Set<String> = []
    private(set) var occasions: [Occasion] = []
    private(set) var people: [String: Person] = [:]
    private(set) var loaded = false

    init(backend: any Backend, personId: String) { self.backend = backend; self.personId = personId }

    var isMe: Bool { backend.currentUser?.id == personId }
    var next: UpcomingOccasion? {
        Overview.upcoming(occasions.filter { $0.celebratedId == personId }, people: people, today: .now).first
    }

    func observe() async {
        let peopleStream = backend.people(), wishStream = backend.wishes(of: personId)
        let claimStream = backend.claims(onWishesOf: personId), occasionStream = backend.occasions()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { @MainActor in for await value in peopleStream { self.people = value; self.person = value[self.personId] } }
            group.addTask { @MainActor in for await value in wishStream { self.wishes = value.filter { $0.status == .active }; self.loaded = true } }
            group.addTask { @MainActor in for await value in claimStream { self.claimed = Set(value.keys) } }
            group.addTask { @MainActor in for await value in occasionStream { self.occasions = value } }
        }
    }
}

struct PersonView: View {
    @State var model: PersonModel
    var openWish: (Wish) -> Void
    var addWish: () -> Void

    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.m) {
                HStack(spacing: Space.m) {
                    PersonAvatar(person: model.person, size: 64)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(model.person?.displayName ?? "").textStyle(.displayLarge).foregroundStyle(AppColor.ink)
                        if let next = model.next {
                            let title = next.occasion.kind == .birthday ? Fmt.kindTitle(.birthday) : Fmt.occasionTitle(next, meId: nil)
                            Text("\(title) · \(Fmt.relativeDays(next.daysUntil))").textStyle(.callout).foregroundStyle(AppColor.inkMuted)
                        }
                    }
                }
                if !model.isMe, let about = model.person?.aboutMe { AboutMeCard(about: about) }

                if model.loaded && model.wishes.isEmpty {
                    if model.isMe {
                        EmptyStateView(L("my_list_empty_title"), message: L("my_list_empty_body")) {
                            AppButton(L("action_add_wish"), systemImage: "plus", action: addWish).fixedSize()
                        }
                    } else {
                        EmptyStateView(L("person_empty_title"), message: L("person_empty_body", model.person?.displayName ?? ""))
                    }
                }

                LazyVGrid(columns: columns, spacing: Space.l) {
                    ForEach(Array(model.wishes.enumerated()), id: \.element.id) { index, wish in
                        Button { openWish(wish) } label: {
                            // Claims are only shown to people who aren't the owner.
                            WishCard(wish: wish, wrapped: !model.isMe && model.claimed.contains(wish.id))
                        }
                        .buttonStyle(PressableStyle())
                    }
                }
                if model.wishes.count >= 12 { NativeAdCard() }
            }
            .padding(.horizontal, Space.l)
            .padding(.bottom, Space.xxl)
        }
        .screenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.observe() }
    }
}

// MARK: - Wish detail

@MainActor
@Observable
final class WishModel {
    private let backend: any Backend
    let wishId: String
    let ownerId: String
    private(set) var wish: Wish?
    private(set) var owner: Person?
    private(set) var claims: [Claim] = []
    private(set) var busy = false
    var failed = false

    init(backend: any Backend, wishId: String, ownerId: String) {
        self.backend = backend; self.wishId = wishId; self.ownerId = ownerId
    }

    var isMine: Bool { backend.currentUser?.id == ownerId }
    var myClaim: Claim? { claims.first { $0.claimerId == backend.currentUser?.id } }
    var otherClaim: Claim? { claims.first { $0.claimerId != backend.currentUser?.id } }

    func observe() async {
        let wishStream = backend.wish(wishId), peopleStream = backend.people(), claimStream = backend.claims(onWishesOf: ownerId)
        await withTaskGroup(of: Void.self) { group in
            group.addTask { @MainActor in for await value in wishStream { self.wish = value } }
            group.addTask { @MainActor in for await value in peopleStream { self.owner = value[self.ownerId] } }
            group.addTask { @MainActor in
                for await value in claimStream {
                    // Defense in depth: never surface claims to the wish's owner.
                    self.claims = self.isMine ? [] : value[self.wishId] ?? []
                }
            }
        }
    }

    func toggleClaim() async {
        guard let wish else { return }
        await run { [backend] in
            if self.myClaim == nil { try await backend.claim(wish) } else { try await backend.release(wish.id) }
        }
    }

    func markReceived() async { await run { [backend, wishId] in try await backend.markReceived(wishId) } }
    func delete() async { await run { [backend, wishId] in try await backend.deleteWish(wishId) } }

    private func run(_ action: @MainActor () async throws -> Void) async {
        busy = true
        defer { busy = false }
        do { try await action() } catch { failed = true }
    }
}

struct WishDetailView: View {
    @State var model: WishModel
    var edit: (Wish) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var confirmDelete = false

    var body: some View {
        Group {
            if let wish = model.wish {
                content(wish)
            } else {
                Color.clear
            }
        }
        .screenBackground()
        .navigationTitle(model.owner.map { L("wishes_of", $0.displayName) } ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbar }
        .task { await model.observe() }
        .confirmationDialog(L("delete_wish_title"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(L("action_delete"), role: .destructive) { Task { await model.delete(); dismiss() } }
        } message: { Text(L("delete_wish_body")) }
        .alert(L("error_generic"), isPresented: $model.failed) { Button("OK") {} }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            if model.isMine, let wish = model.wish {
                Menu {
                    Button(L("action_edit"), systemImage: "pencil") { edit(wish) }
                    Button(L("mark_received"), systemImage: "checkmark") { Task { await model.markReceived() } }
                    Button(L("action_delete"), systemImage: "trash", role: .destructive) { confirmDelete = true }
                } label: { Image(systemName: "ellipsis") }
            } else if let wish = model.wish {
                ShareLink(item: wish.urls.first?.absoluteString ?? wish.title)
            }
        }
    }

    private func content(_ wish: Wish) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.s) {
                WrapStage(isWrapped: model.myClaim != nil) {
                    WishImage(wish: wish, symbolSize: 110)
                }
                .frame(height: 320)
                .padding(.bottom, Space.xs)

                HStack(spacing: Space.xs) {
                    PersonAvatar(person: model.owner, size: 26)
                    Text([model.owner?.displayName, Fmt.priority(wish.priority)].compactMap { $0 }.joined(separator: " · "))
                        .textStyle(.callout).foregroundStyle(AppColor.inkMuted)
                }
                Text(wish.title).textStyle(.displayMedium).foregroundStyle(AppColor.ink)
                let meta = [wish.price.map(Fmt.money), wish.store].compactMap { $0 }.joined(separator: " · ")
                if !meta.isEmpty { Text(meta).textStyle(.body).foregroundStyle(AppColor.inkMuted) }
                if let note = wish.note {
                    Text("“\(note)”").font(AppFonts.display(size: 19, relativeTo: .title3)).italic()
                        .foregroundStyle(AppColor.ink).padding(.top, Space.xs)
                }
                if !model.isMine { hint }
            }
            .padding(.horizontal, Space.l)
            .padding(.bottom, 120)
        }
        .safeAreaInset(edge: .bottom) { actions(wish) }
    }

    private var hint: some View {
        let name = model.owner?.displayName ?? ""
        let text: String = if model.myClaim != nil {
            L("claim_hint_mine", name)
        } else if let other = model.otherClaim {
            L("claim_hint_other", other.claimerName, name)
        } else {
            L("claim_hint_open", name)
        }
        return Label(text, systemImage: "lock").textStyle(.caption).foregroundStyle(AppColor.inkMuted).padding(.top, Space.xs)
    }

    private func actions(_ wish: Wish) -> some View {
        HStack(spacing: Space.s) {
            if let url = wish.urls.first {
                Button { openURL(url) } label: {
                    Image(systemName: "arrow.up.right.square").font(.title3).foregroundStyle(AppColor.ink)
                        .frame(width: 56, height: 56).background(AppColor.surfaceMuted, in: RoundedRectangle(cornerRadius: 18))
                }
                .buttonStyle(PressableStyle())
                .accessibilityLabel(L("open_in_store"))
            }
            if model.isMine {
                AppButton(L("action_edit"), systemImage: "pencil", kind: .secondary) { edit(wish) }
            } else if model.myClaim != nil {
                AppButton(L("claim_undo"), systemImage: "checkmark", kind: .ribbon, isLoading: model.busy) { Task { await model.toggleClaim() } }
            } else {
                AppButton(L("claim_action"), systemImage: "gift", isLoading: model.busy) { Task { await model.toggleClaim() } }
            }
        }
        .padding(.horizontal, Space.l)
        .padding(.vertical, Space.s)
        .background(AppColor.canvas)
    }
}

// MARK: - My list

@MainActor
@Observable
final class MyListModel {
    private let backend: any Backend
    private(set) var wishes: [Wish] = []
    private(set) var loaded = false
    init(backend: any Backend) { self.backend = backend }

    func observe() async {
        for await value in backend.myWishes() {
            wishes = value.filter { $0.status == .active }
            loaded = true
        }
    }
}

struct MyListView: View {
    @State var model: MyListModel
    var openWish: (Wish) -> Void
    var addWish: () -> Void
    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.l) {
                if model.loaded && model.wishes.isEmpty {
                    EmptyStateView(L("my_list_empty_title"), message: L("my_list_empty_body")) {
                        AppButton(L("action_add_wish"), systemImage: "plus", action: addWish).fixedSize()
                    }
                }
                // Your own list never shows whether anything was claimed.
                LazyVGrid(columns: columns, spacing: Space.l) {
                    ForEach(model.wishes) { wish in
                        Button { openWish(wish) } label: { WishCard(wish: wish) }.buttonStyle(PressableStyle())
                    }
                }
                if model.wishes.count >= 8 { InlineBanner() }
            }
            .padding(.horizontal, Space.l)
            .padding(.bottom, Space.xxl)
        }
        .screenBackground()
        .navigationTitle(L("my_list_title"))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: addWish) { Image(systemName: "plus") }.accessibilityLabel(L("action_add_wish"))
            }
        }
        .task { await model.observe() }
    }
}
