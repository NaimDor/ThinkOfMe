import Core
import DesignSystem
import SwiftUI

enum Route: Hashable {
    case person(String)
    case wish(id: String, ownerId: String)
    case settings
}

/// What a sheet is currently showing.
enum SheetRoute: Identifiable {
    case editWish(id: String?, sharedURL: URL?)
    case join(code: String)

    var id: String {
        switch self {
        case .editWish(let id, let url): "edit-\(id ?? "new")-\(url?.absoluteString ?? "")"
        case .join(let code): "join-\(code)"
        }
    }
}

struct RootView: View {
    @Bindable var app: AppModel
    let services: AppServices

    var body: some View {
        Group {
            switch app.gate {
            case .loading:
                AppColor.canvas.ignoresSafeArea()
            case .signedOut:
                SignInView(backend: app.backend, googleSignIn: services.googleSignIn)
            case .needsProfile:
                OnboardingView(backend: app.backend) { app.profileSaved() }
            case .ready:
                MainTabs(app: app, services: services)
                    .task { await services.registerDevice(app.backend) }
            }
        }
        .animation(.easeInOut(duration: 0.3), value: app.gate)
        .task { app.start() }
        .onOpenURL { app.open($0) }
    }
}

private enum TabID: Hashable { case home, myList, circles, dates }

struct MainTabs: View {
    @Bindable var app: AppModel
    let services: AppServices
    @State private var tab: TabID = .home
    @State private var homePath = NavigationPath()
    @State private var listPath = NavigationPath()
    @State private var circlesPath = NavigationPath()
    @State private var datesPath = NavigationPath()
    @State private var sheet: SheetRoute?

    private var backend: any Backend { app.backend }

    var body: some View {
        TabView(selection: $tab) {
            Tab(L("tab_home"), systemImage: "house", value: .home) {
                NavigationStack(path: $homePath) {
                    HomeView(
                        model: HomeModel(backend: backend),
                        openPerson: { homePath.append(Route.person($0)) },
                        openSettings: { homePath.append(Route.settings) },
                        createCircle: { tab = .circles },
                        isDemo: backend.isDemo
                    )
                    .toolbar { addButton }
                    .destinations(backend: backend, path: $homePath, sheet: $sheet, privacyOptions: services.privacyOptions)
                }
            }
            Tab(L("tab_my_list"), systemImage: "heart", value: .myList) {
                NavigationStack(path: $listPath) {
                    MyListView(model: MyListModel(backend: backend), openWish: { listPath.append(Route.wish(id: $0.id, ownerId: $0.ownerId)) }, addWish: add)
                        .destinations(backend: backend, path: $listPath, sheet: $sheet, privacyOptions: services.privacyOptions)
                }
            }
            Tab(L("tab_circles"), systemImage: "person.2", value: .circles) {
                NavigationStack(path: $circlesPath) {
                    CirclesView(model: CirclesModel(backend: backend), openPerson: { circlesPath.append(Route.person($0)) }, join: { sheet = .join(code: $0) })
                        .destinations(backend: backend, path: $circlesPath, sheet: $sheet, privacyOptions: services.privacyOptions)
                }
            }
            Tab(L("tab_dates"), systemImage: "calendar", value: .dates) {
                NavigationStack(path: $datesPath) {
                    OccasionsView(model: OccasionsModel(backend: backend), openPerson: { datesPath.append(Route.person($0)) })
                        .destinations(backend: backend, path: $datesPath, sheet: $sheet, privacyOptions: services.privacyOptions)
                }
            }
        }
        .tint(AppColor.accentText)
        .sheet(item: $sheet) { route in
            switch route {
            case .editWish(let id, let url):
                EditWishView(model: EditWishModel(backend: backend, wishId: id, sharedURL: url))
            case .join(let code):
                JoinView(model: JoinModel(backend: backend, code: code))
            }
        }
        .onChange(of: app.incoming, initial: true) { _, link in
            guard let link else { return }
            switch link {
            case .invite(let code): sheet = .join(code: code)
            case .sharedURL(let url): sheet = .editWish(id: nil, sharedURL: url)
            }
            app.incoming = nil
        }
    }

    private func add() { sheet = .editWish(id: nil, sharedURL: nil) }

    @ToolbarContentBuilder
    private var addButton: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Button(action: add) { Image(systemName: "plus") }.accessibilityLabel(L("action_add_wish"))
        }
    }
}

private extension View {
    func destinations(backend: any Backend, path: Binding<NavigationPath>, sheet: Binding<SheetRoute?>, privacyOptions: (() -> Void)?) -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .person(let id):
                PersonView(
                    model: PersonModel(backend: backend, personId: id),
                    openWish: { path.wrappedValue.append(Route.wish(id: $0.id, ownerId: $0.ownerId)) },
                    addWish: { sheet.wrappedValue = .editWish(id: nil, sharedURL: nil) }
                )
            case .wish(let id, let ownerId):
                WishDetailView(model: WishModel(backend: backend, wishId: id, ownerId: ownerId)) { wish in
                    sheet.wrappedValue = .editWish(id: wish.id, sharedURL: nil)
                }
            case .settings:
                SettingsView(backend: backend, privacyOptions: privacyOptions)
            }
        }
    }
}
