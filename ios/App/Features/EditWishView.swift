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
    /// The photo being read, shown under the magnifying glass while it works.
    private(set) var readingPhotoImage: UIImage?
    /// Some fields were filled in from the photo.
    /// What was filled in from the photo, so it can be cleared again.
    private(set) var photoFill: PhotoFill?
    /// Whether the last automatic fill came from a link (else from a photo).
    private(set) var filledFromLink = false
    private var lastUnfurledURL: String?
    private var urlTask: Task<Void, Never>?
    /// Upload URL of the photo the details were read from.
    private var analyzedPhotoURL: URL?
    private var readTask: Task<Void, Never>?
    /// The photo the details came from was removed: ask whether to clear them too.
    var askClearAfterRemove = false
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
    /// Typing or pasting a link reads it automatically once it looks like a whole address.
    func urlChanged() {
        urlTask?.cancel()
        let text = draft.url.trimmingCharacters(in: .whitespaces)
        guard let url = URL(string: text), url.scheme?.hasPrefix("http") == true,
              url.host()?.contains(".") == true, text != lastUnfurledURL else { return }
        urlTask = Task {
            try? await Task.sleep(for: .milliseconds(700))
            guard !Task.isCancelled else { return }
            await unfurl()
        }
    }

    /// Fetch title, photos, price and store for the link. Fields the user already typed win.
    func unfurl() async {
        let text = draft.url.trimmingCharacters(in: .whitespaces)
        guard let url = URL(string: text), url.scheme?.hasPrefix("http") == true else { return }
        if unfurling && text == lastUnfurledURL { return }
        lastUnfurledURL = text
        unfurling = true
        unfurlFailed = false
        defer { unfurling = false }
        let preview: LinkPreview
        do { preview = try await backend.unfurl(url, wishId: wishId) } catch {
            unfurlFailed = true
            return
        }
        let merged = draft.merging(preview)
        if let fill = PhotoFill(before: draft, merged: merged) {
            photoFill = photoFill?.combined(with: fill) ?? fill
            filledFromLink = true
        }
        draft = merged
        unfurlFailed = preview.title == nil && preview.imageURLs.isEmpty
    }

    /// Downscales a picked photo to a JPEG and uploads it.
    func addPhoto(_ item: PhotosPickerItem) async {
        guard draft.imageURLs.count < 8 else { return }
        uploadingPhoto = true
        defer { uploadingPhoto = false }
        guard let data = try? await item.loadTransferable(type: Data.self),
              let jpeg = Self.encode(data) else { failed = true; return }
        let reads = draft.needsDetails && !readingPhoto
        if reads { readTask = Task { await readPhoto(jpeg) } }
        do {
            let url = try await backend.uploadPhoto(jpeg, wishId: wishId)
            draft.imageURLs.insert(url, at: 0)
            if reads { analyzedPhotoURL = url }
        } catch {
            report(error, "uploadPhoto")
            failed = true
        }
    }

    private func readPhoto(_ jpeg: Data) async {
        readingPhoto = true
        readingPhotoImage = UIImage(data: jpeg)
        defer { readingPhoto = false; readingPhotoImage = nil }
        let locale = Locale.current.identifier
        let details: PhotoDetails
        do { details = try await backend.analyzePhoto(jpeg, locale: locale) } catch {
            if !Task.isCancelled { report(error, "analyzePhoto") }
            return
        }
        // The photo was removed while it was being read.
        guard !Task.isCancelled, details.isProduct else { return }
        let merged = draft.merging(details)
        if let fill = PhotoFill(before: draft, merged: merged) {
            photoFill = photoFill?.combined(with: fill) ?? fill
            filledFromLink = false
        }
        draft = merged
        photoSearchQuery = draft.url.isEmpty ? details.searchQuery : nil
        if draft.url.hasPrefix("http"), draft.imageURLs.count <= 1 { await unfurl() }
    }

    /// Removing the photo the details came from stops a read in progress, or offers to clear them.
    func removePhoto(_ url: URL) {
        draft.imageURLs.removeAll { $0 == url }
        guard url == analyzedPhotoURL else { return }
        analyzedPhotoURL = nil
        if readingPhoto { readTask?.cancel() }
        askClearAfterRemove = photoFill != nil
    }

    /// Takes back what the photo filled in (fields the user has since edited stay).
    func clearPhotoDetails() {
        if let photoFill { draft = draft.clearing(photoFill) }
        photoFill = nil
        photoSearchQuery = nil
        askClearAfterRemove = false
    }

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
                        .onChange(of: model.draft.url) { model.urlChanged() }
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
            .alert(L("clear_photo_title"), isPresented: $model.askClearAfterRemove) {
                Button(L("clear_photo_details"), role: .destructive) { withAnimation { model.clearPhotoDetails() } }
                Button(L("keep_photo_details"), role: .cancel) {}
            } message: {
                Text(L("clear_photo_body"))
            }
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

/// While reading: the photo under a moving magnifying glass. After: what was filled in, and a web search.
private struct PhotoReadingStatus: View {
    let model: EditWishModel
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            if model.readingPhoto, let image = model.readingPhotoImage {
                PhotoScanner(image: image).transition(.opacity.combined(with: .scale(scale: 0.97)))
            } else {
                if model.photoFill != nil {
                    HStack(spacing: 8) {
                        Image(systemName: "sparkles").foregroundStyle(AppColor.accentText)
                        Text(L(model.filledFromLink ? "filled_from_link" : "filled_from_photo")).textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                        Spacer(minLength: Space.xs)
                        Button(L("clear_photo_details")) { withAnimation { model.clearPhotoDetails() } }
                            .textStyle(.callout).fontWeight(.semibold).foregroundStyle(AppColor.accentText)
                    }
                }
                if let query = model.photoSearchQuery,
                   let url = URL(string: "https://www.google.com/search?q=" + (query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")) {
                    Button(L("find_online")) { openURL(url) }
                        .textStyle(.callout).fontWeight(.semibold).foregroundStyle(AppColor.accentText)
                }
            }
        }
        .animation(.easeOut(duration: 0.25), value: model.readingPhoto)
    }
}

/// The photo with a magnifying glass gliding over it in a figure-8, the lens showing the
/// photo zoomed in, and a line saying what it is looking for. Still when Reduce Motion is on.
struct PhotoScanner: View {
    let image: UIImage
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var step = 0
    private let steps = ["scan_looking", "scan_brand", "scan_price", "scan_store"]

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s) {
            TimelineView(.animation(paused: reduceMotion)) { context in
                let cycle = 4.2
                let t = reduceMotion ? 0.12 : context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: cycle) / cycle
                GeometryReader { geo in
                    lensScene(size: geo.size, t: t)
                }
            }
            .frame(height: 190)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))

            HStack(spacing: 8) {
                Image(systemName: "sparkles").foregroundStyle(AppColor.accentText)
                Text(L(steps[step]))
                    .textStyle(.callout).foregroundStyle(AppColor.inkMuted)
                    .id(step)
                    .transition(.opacity)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("reading_photo"))
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1.6))
                withAnimation(.easeInOut(duration: 0.3)) { step = (step + 1) % steps.count }
            }
        }
    }

    private func lensScene(size: CGSize, t: Double) -> some View {
        let angle = 2 * Double.pi * t
        let center = CGPoint(x: size.width * (0.5 + 0.30 * sin(angle)), y: size.height * (0.5 + 0.20 * sin(2 * angle)))
        let r = size.height * 0.26
        let handleStart = CGPoint(x: center.x + r * 0.72, y: center.y + r * 0.72)
        let handleEnd = CGPoint(x: center.x + r * 1.32, y: center.y + r * 1.32)
        return ZStack {
            photo(size).overlay(Color.black.opacity(0.28))
            // Soft shadow so the lens floats above the photo.
            Circle().fill(Color.black.opacity(0.22))
                .frame(width: r * 2.12, height: r * 2.12)
                .position(x: center.x + r * 0.10, y: center.y + r * 0.14)
            // Zoomed photo inside the lens.
            photo(size)
                .scaleEffect(1.9, anchor: UnitPoint(x: center.x / size.width, y: center.y / size.height))
                .mask { Circle().frame(width: r * 2, height: r * 2).position(center) }
            Path { p in p.move(to: handleStart); p.addLine(to: handleEnd) }
                .stroke(AppColor.surface, style: StrokeStyle(lineWidth: r * 0.22, lineCap: .round))
            Path { p in p.move(to: CGPoint(x: handleStart.x + r * 0.18, y: handleStart.y + r * 0.18)); p.addLine(to: handleEnd) }
                .stroke(AppColor.accent, style: StrokeStyle(lineWidth: r * 0.14, lineCap: .round))
            Circle().stroke(AppColor.surface, lineWidth: r * 0.13)
                .frame(width: r * 2, height: r * 2).position(center)
            Circle().trim(from: 0.555, to: 0.695)
                .stroke(Color.white.opacity(0.7), style: StrokeStyle(lineWidth: r * 0.07, lineCap: .round))
                .frame(width: r * 1.4, height: r * 1.4).position(center)
        }
        .frame(width: size.width, height: size.height)
    }

    private func photo(_ size: CGSize) -> some View {
        Image(uiImage: image).resizable().scaledToFill().frame(width: size.width, height: size.height).clipped()
    }
}
