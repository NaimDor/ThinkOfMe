import Core
import DesignSystem
import SwiftUI

@MainActor
@Observable
final class CirclesModel {
    private let backend: any Backend
    private(set) var circles: [Circle] = []
    private(set) var people: [String: Person] = [:]
    private(set) var loaded = false
    var shareURL: URL?
    var failed = false

    init(backend: any Backend) { self.backend = backend }

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
        } catch { failed = true }
    }

    func invite(_ circleId: String) async {
        do { shareURL = try await backend.createInvite(circleId: circleId).url } catch { failed = true }
    }
}

struct CirclesView: View {
    @State var model: CirclesModel
    var openPerson: (String) -> Void
    var join: (String) -> Void
    @State private var creating = false
    @State private var joining = false

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
        .task { await model.observe() }
    }

    private func card(_ circle: Circle) -> some View {
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
        do { _ = try await backend.joinWithInvite(code: code); joined = true } catch { failed = true }
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
