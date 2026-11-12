import FirebaseCore
import FirebaseCrashlytics
import Foundation
import os

private let log = Logger(subsystem: "com.thinkofme.app", category: "errors")

/// Records a failed user action so it shows in Crashlytics (as a non-fatal with the
/// Firebase error code and message), while the UI shows its friendly generic message.
func report(_ error: Error, _ action: String) {
    let ns = error as NSError
    log.error("\(action, privacy: .public) failed: \(ns.domain, privacy: .public) \(ns.code) \(ns.localizedDescription, privacy: .public)")
    guard FirebaseApp.app() != nil else { return }
    Crashlytics.crashlytics().log("\(action) failed")
    Crashlytics.crashlytics().record(error: error, userInfo: ["action": action])
}
