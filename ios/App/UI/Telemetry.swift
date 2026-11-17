import FirebaseAnalytics
import FirebaseCore
import SwiftUI

/// Usage analytics (Firebase Analytics). Counts what people do, never what they write:
/// parameters are kinds, yes/no flags and counts, never titles, notes, names, links or ids.
/// Event and screen names match Android (core/data Telemetry.kt), see docs/ANALYTICS.md.
/// Does nothing in demo builds and store-screenshot runs (Firebase isn't configured).
enum Telemetry {
    static func event(_ name: String, _ params: [String: Any] = [:]) {
        guard FirebaseApp.app() != nil else { return }
        let values = params.mapValues { value -> Any in (value as? Bool).map { $0 ? 1 : 0 } ?? value }
        Analytics.logEvent(name, parameters: values.isEmpty ? nil : values)
    }

    static func screen(_ name: String) {
        event(AnalyticsEventScreenView, [AnalyticsParameterScreenName: name, AnalyticsParameterScreenClass: name])
    }
}

extension View {
    /// Logs a screen view each time the screen appears.
    func trackScreen(_ name: String) -> some View { onAppear { Telemetry.screen(name) } }
}
