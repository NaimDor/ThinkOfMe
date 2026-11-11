import Core
import DesignSystem
import SwiftUI

@MainActor
@Observable
final class EditWishModel {
    private let backend: any Backend
    let wishId: String?
    var draft = WishDraft()
    private(set) var circles: [Circle] = []
    private(set) var unfurling = false
    private(set) var unfurlFailed = false
    private(set) var saving = false
    var failed = false

    init(backend: any Backend, wishId: String?, sharedURL: URL? = nil) {
        self.backend = backend
        self.wishId = wishId
        if let sharedURL { draft.url = sharedURL.absoluteString }
    }

    var isNew: Bool { wishId == nil }

    func load() async {
        if let wishId {
            var iterator = backend.wish(wishId).makeAsyncIterator()
            if let wish = await iterator.next() ?? nil { draft = WishDraft(wish: wish) }
        } else if !draft.url.isEmpty {
            await unfurl()
        }
        for await value in backend.circles() { circles = value }
    }

    /// Fill title, photos and price from the link. Fields the user typed win.
    func unfurl() async {
        guard let url = URL(string: draft.url.trimmingCharacters(in: .whitespaces)), url.scheme?.hasPrefix("http") == true else { return }
        unfurling = true
        unfurlFailed = false
        defer { unfurling = false }
        guard let preview = try? await backend.unfurl(url) else { unfurlFailed = true; return }
        if draft.title.isEmpty { draft.title = preview.title ?? "" }
        if draft.imageURLs.isEmpty { draft.imageURLs = Array(preview.imageURLs.prefix(4)) }
        if draft.priceText.isEmpty, let price = preview.price {
            draft.priceText = WishDraft.format(price.amount)
            draft.currency = price.currency
        }
        if draft.store.isEmpty { draft.store = preview.siteName ?? "" }
        unfurlFailed = preview.title == nil && preview.imageURLs.isEmpty
    }

    func save() async -> Bool {
        guard draft.isValid, !saving else { return false }
        saving = true
        defer { saving = false }
        do {
            _ = try await backend.saveWish(id: wishId, draft: draft)
            return true
        } catch {
            failed = true
            return false
        }
    }
}

struct EditWishView: View {
    @State var model: EditWishModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Space.l) {
                    AppTextField(L("field_link"), text: $model.draft.url, placeholder: L("field_link_hint"))
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onSubmit { Task { await model.unfurl() } }
                        .overlay(alignment: .bottomTrailing) {
                            if model.unfurling {
                                ProgressView().padding(14)
                            } else if model.draft.url.hasPrefix("http") {
                                Button { Task { await model.unfurl() } } label: {
                                    Image(systemName: "link").foregroundStyle(AppColor.accentText).padding(14)
                                }
                                .accessibilityLabel(L("fetching_link"))
                            }
                        }
                    if model.unfurling || model.unfurlFailed {
                        Text(L(model.unfurling ? "fetching_link" : "link_failed")).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                    }
                    if !model.draft.imageURLs.isEmpty {
                        ScrollView(.horizontal) {
                            HStack(spacing: Space.xs) {
                                ForEach(model.draft.imageURLs, id: \.self) { url in
                                    AsyncImage(url: url) { $0.resizable().scaledToFill() } placeholder: { AppColor.surfaceMuted }
                                        .frame(width: 96, height: 96)
                                        .clipShape(RoundedRectangle(cornerRadius: Radius.m, style: .continuous))
                                }
                            }
                        }
                        .scrollIndicators(.hidden)
                    }

                    HStack(spacing: Space.xs) {
                        ChoiceChip(L("field_kind_product"), systemImage: "gift", isSelected: model.draft.kind == .product) { model.draft.kind = .product }
                        ChoiceChip(L("field_kind_idea"), systemImage: "sparkle", isSelected: model.draft.kind == .idea) { model.draft.kind = .idea }
                    }

                    AppTextField(L("field_title"), text: $model.draft.title)
                    if model.draft.kind == .product {
                        HStack(alignment: .top, spacing: Space.s) {
                            AppTextField(L("field_price"), text: $model.draft.priceText).keyboardType(.decimalPad)
                            AppTextField(L("field_store"), text: $model.draft.store)
                        }
                    }
                    AppTextField(L("field_note"), text: $model.draft.note, placeholder: L("field_note_hint"), axis: .vertical)

                    section(L("how_much")) {
                        FlowLayout(spacing: Space.xs) {
                            ForEach(Priority.allCases, id: \.self) { p in
                                ChoiceChip(Fmt.priority(p), isSelected: model.draft.priority == p) { model.draft.priority = p }
                            }
                        }
                    }
                    section(L("visible_to")) {
                        FlowLayout(spacing: Space.xs) {
                            ChoiceChip(L("visible_all"), systemImage: "person.2", isSelected: model.draft.visibleCircleIds.contains(allCircles)) {
                                model.draft.toggleCircle(allCircles)
                            }
                            ForEach(model.circles) { circle in
                                ChoiceChip(circle.name, systemImage: circle.kind == .pair ? "heart" : "person.3",
                                           isSelected: model.draft.visibleCircleIds.contains(circle.id)) {
                                    model.draft.toggleCircle(circle.id)
                                }
                            }
                        }
                        if model.draft.visibleCircleIds.isEmpty {
                            Label(L("visible_to_none"), systemImage: "lock").textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                        }
                    }
                }
                .padding(Space.l)
            }
            .screenBackground()
            .safeAreaInset(edge: .bottom) {
                AppButton(L("action_save"), isLoading: model.saving) {
                    Task { if await model.save() { dismiss() } }
                }
                .disabled(!model.draft.isValid)
                .padding(.horizontal, Space.l)
                .padding(.vertical, Space.s)
                .background(AppColor.canvas)
            }
            .navigationTitle(L(model.isNew ? "new_wish" : "edit_wish"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("action_cancel")) { dismiss() } }
            }
            .alert(L("error_generic"), isPresented: $model.failed) { Button("OK") {} }
            .task { await model.load() }
        }
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            Text(title).textStyle(.caption).fontWeight(.semibold).foregroundStyle(AppColor.inkMuted)
            content()
        }
    }
}

/// Wrapping row layout for chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(proposal.width ?? .infinity, subviews)
        let height = rows.reduce(0) { $0 + $1.height } + spacing * CGFloat(max(0, rows.count - 1))
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(bounds.width, subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row { var indices: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func arrange(_ maxWidth: CGFloat, _ subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            if rows[rows.count - 1].width + size.width > maxWidth, !rows[rows.count - 1].indices.isEmpty {
                rows.append(Row())
            }
            let gap = rows[rows.count - 1].indices.isEmpty ? 0 : spacing
            rows[rows.count - 1].indices.append(index)
            rows[rows.count - 1].width += gap + size.width
            rows[rows.count - 1].height = max(rows[rows.count - 1].height, size.height)
        }
        return rows
    }
}
