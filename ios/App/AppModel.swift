import Core
import Foundation
import Observation

enum AppGate: Equatable {
    case loading, signedOut, needsProfile, ready
}

/// Something the app was opened with.
enum IncomingLink: Equatable, Identifiable {
    case invite(code: String)
    case sharedURL(URL)

    var id: String {
        switch self {
        case .invite(let code): "invite-\(code)"
        case .sharedURL(let url): "url-\(url.absoluteString)"
        }
    }

    /// https://thinkofme.app/i/CODE
    static func parse(_ url: URL) -> IncomingLink? {
        let parts = url.pathComponents.filter { $0 != "/" }
        if url.host() == "thinkofme.app", parts.first == "i", let code = parts.dropFirst().first {
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
