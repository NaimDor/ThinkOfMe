import Core
import DesignSystem
import SwiftUI

@MainActor
@Observable
final class CirclesModel {
    private let backend: any Backend
    private(set) var circles: [GiftCircle] = []
    private(set) var people: [String: Person] = [:]
    private(set) var loaded = false
    var shareURL: URL?
    var failed = false

    init(backend: any Backend) { self.backend = backend }

    var meId: String? { backend.currentUser?.id }
    func isAdmin(_ circle: GiftCircle) -> Bool { meId.map(circle.adminIds.contains) ?? false }

    func observe() async {
        let circleStream = backend.circles(), peopleStream = backend.people()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { @MainActor in for await value in circleStream { self.circles = value; self.loaded = true } }
            group.addTask { @MainActor in for await value in peopleStream { self.people = value } }
        }
    }

    func create(name: String, kind: CircleKind) async {
        do {
            let id = try await backend.createCircle(name: name, kind: kind)
            await invite(id)
        } catch { report(error, "createCircle"); failed = true }
    }

    func invite(_ circleId: String) async {
        do { shareURL = try await backend.createInvite(circleId: circleId).url } catch { report(error, "createInvite"); failed = true }
    }

    func rename(_ circleId: String, to name: String) async {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        do { try await backend.renameCircle(circleId, name: String(trimmed.prefix(60))) } catch { report(error, "renameCircle"); failed = true }
    }

    func remove(_ userId: String, from circleId: String) async {
        do { try await backend.removeMember(userId, from: circleId) } catch { report(error, "removeMember"); failed = true }
    }

    func leave(_ circleId: String) async {
        do { try await backend.leaveCircle(circleId) } catch { report(error, "leaveCircle"); failed = true }
    }
}

/// A pending destructive or naming action on a circle, driving the alerts below.
private enum CircleAction: Identifiable {
    case rename(GiftCircle)
    case remove(GiftCircle, Person)
    case leave(GiftCircle)

    var id: String {
        switch self {
        case .rename(let c): "rename-\(c.id)"
        case .remove(let c, let p): "remove-\(c.id)-\(p.id)"
        case .leave(let c): "leave-\(c.id)"
        }
    }
}

struct CirclesView: View {
    @State var model: CirclesModel
    var openPerson: (String) -> Void
    var join: (String) -> Void
    @State private var creating = false
    @State private var joining = false
    @State private var action: CircleAction?
    @State private var newName = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Space.m) {
                if model.loaded && model.circles.isEmpty {
                    EmptyStateView(L("circles_empty_title"), message: L("circles_empty_body")) {
                        AppButton(L("create_circle"), systemImage: "plus") { creating = true }.fixedSize()
                    }
                }
                ForEach(model.circles) { circle in card(circle) }
                Button(L("join_with_code")) { joining = true }
                    .textStyle(.callout).fontWeight(.semibold).foregroundStyle(AppColor.accentText)
                    .frame(maxWidth: .infinity)
                if model.circles.count >= 4 { InlineBanner().padding(.top, Space.l) }
            }
            .padding(.horizontal, Space.l)
            .padding(.bottom, Space.xxl)
        }
        .screenBackground()
        .navigationTitle(L("circles_title"))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { creating = true } label: { Image(systemName: "plus") }.accessibilityLabel(L("create_circle"))
            }
        }
        .sheet(isPresented: $creating) {
            CreateCircleSheet { name, kind in
                creating = false
                Task { await model.create(name: name, kind: kind) }
            }
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $joining) {
            JoinCodeSheet { code in joining = false; join(code) }.presentationDetents([.height(260)])
        }
        .sheet(item: $model.shareURL) { url in
            ShareSheet(items: [L("invite_message", url.absoluteString)])
        }
        .alert(L("error_generic"), isPresented: $model.failed) { Button("OK") {} }
        .alert(alertTitle, isPresented: Binding(get: { action != nil }, set: { if !$0 { action = nil } }), presenting: action) { action in
            switch action {
            case .rename(let circle):
                TextField(L("circle_name"), text: $newName)
                Button(L("action_save")) { Task { await model.rename(circle.id, to: newName) } }
            case .remove(let circle, let person):
                Button(L("remove_member"), role: .destructive) { Task { await model.remove(person.id, from: circle.id) } }
            case .leave(let circle):
                Button(L("leave_circle"), role: .destructive) { Task { await model.leave(circle.id) } }
            }
            Button(L("action_cancel"), role: .cancel) {}
        } message: { action in
            switch action {
            case .rename: EmptyView()
            case .remove(_, let person): Text(L("remove_member_body", person.displayName))
            case .leave: Text(L("leave_circle_body"))
            }
        }
        .task { await model.observe() }
    }

    private var alertTitle: String {
        switch action {
        case .rename: L("circle_rename")
        case .remove: L("remove_member")
        case .leave: L("leave_circle")
        case nil: ""
        }
    }

    private func manageMenu(_ circle: GiftCircle) -> some View {
        Menu {
            if model.isAdmin(circle) {
                Button { newName = circle.name; action = .rename(circle) } label: { Label(L("circle_rename"), systemImage: "pencil") }
            }
            Button(role: .destructive) { action = .leave(circle) } label: { Label(L("leave_circle"), systemImage: "rectangle.portrait.and.arrow.right") }
        } label: {
            Image(systemName: "ellipsis").font(.callout.weight(.semibold)).foregroundStyle(AppColor.ink)
                .frame(width: 36, height: 36).background(AppColor.surfaceMuted, in: .circle)
        }
        .accessibilityLabel(L("circle_manage"))
    }

    private func card(_ circle: GiftCircle) -> some View {
        VStack(alignment: .leading, spacing: Space.xs) {
            HStack {
                VStack(alignment: .leading) {
                    Text(circle.name).textStyle(.title).foregroundStyle(AppColor.ink)
                    Text(circle.kind == .pair ? L("circle_pair") : L("members", circle.memberIds.count))
                        .textStyle(.caption).foregroundStyle(AppColor.inkMuted)
                }
                Spacer()
                if circle.kind == .group || circle.memberIds.count < 2 {
                    Button { Task { await model.invite(circle.id) } } label: {
                        Label(L("invite"), systemImage: "square.and.arrow.up").textStyle(.callout).foregroundStyle(AppColor.ink)
                            .padding(.horizontal, 14).padding(.vertical, 8).background(AppColor.surfaceMuted, in: Capsule())
                    }
                    .buttonStyle(PressableStyle())
                }
                manageMenu(circle)
            }
            Hairline().padding(.vertical, 4)
            ForEach(circle.memberIds.compactMap { model.people[$0] }) { person in
                Button { openPerson(person.id) } label: {
                    HStack(spacing: Space.s) {
                        PersonAvatar(person: person, size: 36)
                        Text(person.displayName).textStyle(.bodyStrong).foregroundStyle(AppColor.ink)
                        Spacer()
                    }
                    .padding(.vertical, 6)
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .contextMenu {
                    if model.isAdmin(circle), person.id != model.meId, person.id != circle.ownerId {
                        Button(role: .destructive) { action = .remove(circle, person) } label: {
                            Label(L("remove_member"), systemImage: "person.badge.minus")
                        }
                    }
                }
            }
        }
        .padding(Space.m)
        .background(AppColor.surface, in: RoundedRectangle(cornerRadius: Radius.l, style: .continuous))
    }
}

struct CreateCircleSheet: View {
    var create: (String, CircleKind) -> Void
    @State private var name = ""
    @State private var kind: CircleKind = .pair

    var body: some View {
        VStack(alignment: .leading, spacing: Space.m) {
            Text(L("create_circle")).textStyle(.title).foregroundStyle(AppColor.ink)
            HStack(spacing: Space.xs) {
                ChoiceChip(L("circle_pair"), systemImage: "heart", isSelected: kind == .pair) { kind = .pair }
                ChoiceChip(L("circle_group"), systemImage: "person.3", isSelected: kind == .group) { kind = .group }
            }
            AppTextField(L("circle_name"), text: $name, placeholder: L("circle_name_hint"))
            AppButton(L("invite"), systemImage: "square.and.arrow.up") { create(name.trimmingCharacters(in: .whitespaces), kind) }
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
            Spacer()
        }
        .padding(Space.l)
        .screenBackground()
    }
}

struct JoinCodeSheet: View {
    var join: (String) -> Void
    @State private var code = ""
    var body: some View {
        VStack(alignment: .leading, spacing: Space.m) {
            Text(L("join_with_code")).textStyle(.title).foregroundStyle(AppColor.ink)
            AppTextField(L("join"), text: $code).textInputAutocapitalization(.characters).autocorrectionDisabled()
            AppButton(L("join")) { join(code) }.disabled(code.count < 8)
        }
        .padding(Space.l)
        .screenBackground()
    }
}

@MainActor
@Observable
final class JoinModel {
    private let backend: any Backend
    let code: String
    private(set) var preview: InvitePreview?
    private(set) var loading = true
    private(set) var joining = false
    private(set) var joined = false
    private(set) var failed = false

    init(backend: any Backend, code: String) { self.backend = backend; self.code = code }

    func load() async {
        preview = try? await backend.previewInvite(code: code)
        failed = preview == nil
        loading = false
    }

    func join() async {
        joining = true
        defer { joining = false }
        do { _ = try await backend.joinWithInvite(code: code); joined = true } catch { report(error, "joinWithInvite"); failed = true }
    }
}

struct JoinView: View {
    @State var model: JoinModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: Space.m) {
            Spacer()
            GiftBoxMoment().frame(width: 140, height: 140)
            if model.loading {
                ProgressView()
            } else if let preview = model.preview {
                Text(L("join_title", preview.circleName)).textStyle(.displayMedium).foregroundStyle(AppColor.ink).multilineTextAlignment(.center)
                let members = preview.kind == .pair ? L("circle_pair") : L("members", preview.memberCount)
                Text(L("join_body", preview.inviterName, members)).textStyle(.body).foregroundStyle(AppColor.inkMuted).multilineTextAlignment(.center)
                AppButton(L("join"), isLoading: model.joining) { Task { await model.join() } }
                AppButton(L("action_cancel"), kind: .secondary) { dismiss() }
            } else {
                Text(L("error_generic")).textStyle(.body).foregroundStyle(AppColor.inkMuted)
                AppButton(L("action_back"), kind: .secondary) { dismiss() }
            }
            Spacer()
        }
        .padding(Space.l)
        .screenBackground()
        .task { await model.load() }
        .onChange(of: model.joined) { _, joined in if joined { dismiss() } }
    }
}

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
