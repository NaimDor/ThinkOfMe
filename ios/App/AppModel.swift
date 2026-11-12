import Core
import Foundation
import Observation

enum AppGate: Equatable {
    case loading, signedOut, needsProfile, ready
}

/// Something the app was opened with: a link, or a tapped notification.
enum IncomingLink: Equatable, Identifiable {
    case invite(code: String)
    case sharedURL(URL)
    case person(id: String)
    case wish(id: String, ownerId: String)
    case tab(String)

    var id: String {
        switch self {
        case .invite(let code): "invite-\(code)"
        case .sharedURL(let url): "url-\(url.absoluteString)"
        case .person(let id): "person-\(id)"
        case .wish(let id, _): "wish-\(id)"
        case .tab(let tab): "tab-\(tab)"
        }
    }

    /// Routing fields the backend puts on every push notification.
    static func fromPush(_ info: [AnyHashable: Any]) -> IncomingLink? {
        switch info["screen"] as? String {
        case "wish":
            guard let id = info["wishId"] as? String, let owner = info["ownerId"] as? String else { return nil }
            return .wish(id: id, ownerId: owner)
        case "person":
            return (info["personId"] as? String).map { .person(id: $0) }
        case let tab? where ["myList", "dates", "home"].contains(tab):
            return .tab(tab)
        default:
            return nil
        }
    }

    static var linkHost: String { Bundle.main.object(forInfoDictionaryKey: "TOMLinkHost") as? String ?? "thinkofme.web.app" }

    /// https://<linkHost>/i/CODE, thinkofme://join?code=CODE, thinkofme://add?url=…
    static func parse(_ url: URL) -> IncomingLink? {
        let parts = url.pathComponents.filter { $0 != "/" }
        if url.scheme == "https", url.host() == linkHost, parts.first == "i", let code = parts.dropFirst().first {
            return .invite(code: code)
        }
        if url.scheme == "thinkofme", url.host() == "join",
           let code = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "code" })?.value {
            return .invite(code: code)
        }
        if url.scheme == "thinkofme", url.host() == "add", let shared = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == "url" })?.value.flatMap(URL.init(string:)) {
            return .sharedURL(shared)
        }
        return nil
    }
}

/// Root state: which backend is in use, who is signed in, and whether
/// onboarding has created their profile yet.
@MainActor
@Observable
final class AppModel {
    let backend: any Backend
    private(set) var gate: AppGate = .loading
    var incoming: IncomingLink?
    private var observation: Task<Void, Never>?

    init(backend: any Backend) {
        self.backend = backend
    }

    func start() {
        guard observation == nil else { return }
        observation = Task { [weak self] in
            guard let backend = self?.backend else { return }
            for await user in backend.session() {
                guard let self else { return }
                if user == nil {
                    gate = .signedOut
                    continue
                }
                // Wait for the first profile value to decide between onboarding and the app.
                var profiles = backend.me().makeAsyncIterator()
                let me = await profiles.next() ?? nil
                gate = me == nil ? .needsProfile : .ready
            }
        }
    }

    /// Onboarding finished: re-check the profile.
    func profileSaved() {
        gate = .ready
    }

    func open(_ url: URL) {
        incoming = IncomingLink.parse(url)
    }
}

/// Hands notification taps from the app delegate to the UI.
@MainActor
@Observable
final class PushRouter {
    static let shared = PushRouter()
    var pending: IncomingLink?
}
