import XCTest

/// Captures the App Store screenshots: every screen in every app language, from the demo
/// world (no ads, no permission prompts). Run by .github/workflows/screenshots.yml, which
/// passes SCREENSHOT_DIR (as TEST_RUNNER_SCREENSHOT_DIR) and sets a clean status bar.
final class StoreScreenshotTests: XCTestCase {
    private let languages = [("en", "en_US"), ("he", "he_IL"), ("ar", "ar_SA"), ("ru", "ru_RU")]
    private let screens = [
        ("01-home", "home"),
        ("02-person", "person:maya"),
        ("03-wrapped", "wish:w4:maya"),
        ("04-my-list", "myList"),
        ("05-dates", "dates"),
        ("06-circles", "circles"),
    ]

    override func setUp() {
        continueAfterFailure = true
    }

    func testCaptureStoreScreenshots() throws {
        let root = URL(fileURLWithPath: ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] ?? NSTemporaryDirectory())
        for (language, locale) in languages {
            let folder = root.appendingPathComponent(language)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            for (name, route) in screens {
                let app = XCUIApplication()
                app.launchArguments = [
                    "-screenshots", "YES",
                    "-screen", route,
                    "-AppleLanguages", "(\(language))",
                    "-AppleLocale", locale,
                ]
                app.launch()
                // Let navigation, images and the wrap animation settle.
                sleep(5)
                let png = XCUIScreen.main.screenshot().pngRepresentation
                try png.write(to: folder.appendingPathComponent("\(name).png"))
                let attachment = XCTAttachment(data: png, uniformTypeIdentifier: "public.png")
                attachment.name = "\(language)-\(name)"
                attachment.lifetime = .keepAlways
                add(attachment)
                app.terminate()
            }
        }
    }
}
