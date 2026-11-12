import Core
import DesignSystem
import PhotosUI
import SwiftUI

@MainActor
@Observable
final class EditWishModel {
    private let backend: any Backend
    /// Stable id, also for a new wish, so photos can be stored under it.
    let wishId: String
    let isNew: Bool
    var draft = WishDraft()
    private(set) var uploadingPhoto = false
    /// The photo is being read for product details.
    private(set) var readingPhoto = false
    /// Some fields were filled in from the photo.
    private(set) var filledFromPhoto = false
    /// A web search that should find the photographed product, when no link is known.
    private(set) var photoSearchQuery: String?
    private(set) var circles: [GiftCircle] = []
    private(set) var unfurling = false
    private(set) var unfurlFailed = false
    private(set) var saving = false
    var failed = false

    init(backend: any Backend, wishId: String?, sharedURL: URL? = nil) {
        self.backend = backend
        self.wishId = wishId ?? backend.newWishId()
        self.isNew = wishId == nil
        if let sharedURL { draft.url = sharedURL.absoluteString }
    }

    func load() async {
        if !isNew {
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

    /// Downscales a picked photo to a JPEG and uploads it.
    func addPhoto(_ item: PhotosPickerItem) async {
        guard draft.imageURLs.count < 8 else { return }
        uploadingPhoto = true
        defer { uploadingPhoto = false }
        guard let data = try? await item.loadTransferable(type: Data.self),
              let jpeg = Self.encode(data) else { failed = true; return }
        if draft.needsDetails && !readingPhoto { Task { await readPhoto(jpeg) } }
        do {
            let url = try await backend.uploadPhoto(jpeg, wishId: wishId)
            draft.imageURLs.insert(url, at: 0)
        } catch {
            report(error, "uploadPhoto")
            failed = true
        }
    }

    private func readPhoto(_ jpeg: Data) async {
        readingPhoto = true
        defer { readingPhoto = false }
        let locale = Locale.current.identifier
        let details: PhotoDetails
        do { details = try await backend.analyzePhoto(jpeg, locale: locale) } catch {
            report(error, "analyzePhoto")
            return
        }
        guard details.isProduct else { return }
        let merged = draft.merging(details)
        filledFromPhoto = merged != draft
        draft = merged
        photoSearchQuery = draft.url.isEmpty ? details.searchQuery : nil
        if draft.url.hasPrefix("http"), draft.imageURLs.count <= 1 { await unfurl() }
    }

    func removePhoto(_ url: URL) { draft.imageURLs.removeAll { $0 == url } }

    /// Longest side 1600 px, JPEG quality 0.85: sharp on a phone, quick to upload.
    nonisolated static func encode(_ data: Data, maxSide: CGFloat = 1600) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        let longest = max(image.size.width, image.size.height)
        let scale = min(1, maxSide / longest)
        let size = CGSize(width: (image.size.width * scale).rounded(), height: (image.size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        return resized.jpegData(compressionQuality: 0.85)
    }

    func save() async -> Bool {
        guard draft.isValid, !saving else { return false }
        saving = true
        defer { saving = false }
        do {
            _ = try await backend.saveWish(id: wishId, isNew: isNew, draft: draft)
            return true
        } catch {
            report(error, "saveWish")
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
                    PhotoRow(model: model)
                    PhotoReadingStatus(model: model)

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

/// An "Add photo" tile (system photo picker) followed by the wish's photos.
private struct PhotoRow: View {
    @Bindable var model: EditWishModel
    @State private var picked: PhotosPickerItem?

    var body: some View {
        ScrollView(.horizontal) {
            HStack(spacing: Space.xs) {
                if model.draft.imageURLs.count < 8 {
                    PhotosPicker(selection: $picked, matching: .images) {
                        VStack(spacing: 4) {
                            if model.uploadingPhoto {
                                ProgressView()
                            } else {
                                Image(systemName: "photo.badge.plus").font(.title3)
                                Text(L("add_photo")).textStyle(.tab)
                            }
                        }
                        .foregroundStyle(AppColor.ink)
                        .frame(width: 96, height: 96)
                        .background(AppColor.surfaceMuted, in: RoundedRectangle(cornerRadius: Radius.m, style: .continuous))
                    }
                    .disabled(model.uploadingPhoto)
                    .onChange(of: picked) { _, item in
                        guard let item else { return }
                        Task { await model.addPhoto(item); picked = nil }
                    }
                }
                ForEach(model.draft.imageURLs, id: \.self) { url in
                    AsyncImage(url: url) { $0.resizable().scaledToFill() } placeholder: { AppColor.surfaceMuted }
                        .frame(width: 96, height: 96)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.m, style: .continuous))
                        .overlay(alignment: .topTrailing) {
                            Button { model.removePhoto(url) } label: {
                                Image(systemName: "xmark").font(.caption.weight(.bold)).foregroundStyle(AppColor.ink)
                                    .frame(width: 28, height: 28).background(AppColor.canvas.opacity(0.85), in: Circle())
                            }
                            .padding(4)
                            .accessibilityLabel(L("action_delete"))
                        }
                }
            }
        }
        .scrollIndicators(.hidden)
    }
}

/// "Reading the photo…" while it works, then what it filled in and a way to find the product online.
private struct PhotoReadingStatus: View {
    let model: EditWishModel
    @Environment(\.openURL) private var openURL

    var body: some View {
        if model.readingPhoto || model.filledFromPhoto || model.photoSearchQuery != nil {
            VStack(alignment: .leading, spacing: Space.xs) {
                HStack(spacing: 8) {
                    if model.readingPhoto {
                        ProgressView().controlSize(.small)
                    } else {
                        Image(systemName: "sparkles").foregroundStyle(AppColor.accentText)
                    }
                    Text(L(model.readingPhoto ? "reading_photo" : "filled_from_photo"))
                        .textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                }
                if !model.readingPhoto, let query = model.photoSearchQuery,
                   let url = URL(string: "https://www.google.com/search?q=" + (query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")) {
                    Button(L("find_online")) { openURL(url) }
                        .textStyle(.callout).fontWeight(.semibold).foregroundStyle(AppColor.accentText)
                }
            }
            .transition(.opacity)
            .animation(.easeOut(duration: 0.2), value: model.readingPhoto)
        }
    }
}

