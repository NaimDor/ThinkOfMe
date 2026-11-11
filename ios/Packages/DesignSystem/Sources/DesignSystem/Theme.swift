import CoreText
import SwiftUI
import UIKit

// MARK: - Colors

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }

    /// A color that follows light / dark mode, from two token values.
    static func adaptive(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

/// Semantic colors from the generated tokens: `AppColor.ink`, `AppColor.accent`…
public enum AppColor {
    private static let l = AppTokens.light
    private static let d = AppTokens.dark

    public static let canvas = Color.adaptive(l.canvas, d.canvas)
    public static let surface = Color.adaptive(l.surface, d.surface)
    public static let surfaceTint = Color.adaptive(l.surfaceTint, d.surfaceTint)
    public static let surfaceMuted = Color.adaptive(l.surfaceMuted, d.surfaceMuted)
    public static let ink = Color.adaptive(l.ink, d.ink)
    public static let inkMuted = Color.adaptive(l.inkMuted, d.inkMuted)
    public static let hairline = Color.adaptive(l.hairline, d.hairline)
    public static let accent = Color.adaptive(l.accent, d.accent)
    public static let onAccent = Color.adaptive(l.onAccent, d.onAccent)
    public static let accentText = Color.adaptive(l.accentText, d.accentText)
    public static let accentTrack = Color.adaptive(l.accentTrack, d.accentTrack)
    public static let ribbon = Color.adaptive(l.ribbon, d.ribbon)
    public static let ribbonDeep = Color.adaptive(l.ribbonDeep, d.ribbonDeep)
    public static let onRibbon = Color.adaptive(l.onRibbon, d.onRibbon)
    public static let positive = Color.adaptive(l.positive, d.positive)
    public static let danger = Color.adaptive(l.danger, d.danger)
}

/// Every person gets a stable signature color. The index uses Java's
/// String.hashCode so iOS and Android pick the same color for the same id.
public enum PersonHue {
    public static func index(for id: String) -> Int {
        var hash: Int32 = 0
        for unit in id.utf16 { hash = hash &* 31 &+ Int32(unit) }
        let count = Int32(AppTokens.personHues.count)
        return Int(((hash % count) + count) % count)
    }

    public static func color(_ index: Int) -> Color {
        let hues = AppTokens.personHues
        return Color(hex: hues[((index % hues.count) + hues.count) % hues.count])
    }
}

// MARK: - Fonts

/// Bundled display faces, one per script. Body text uses the system font so it
/// follows Dynamic Type and the system's best face for each script.
public enum AppFonts {
    private static let registered: Void = {
        let files = [AppTokens.FontFile.latin, AppTokens.FontFile.hebrew, AppTokens.FontFile.cyrillic, AppTokens.FontFile.arabic]
        for file in files {
            if let url = Bundle.module.url(forResource: file, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    public static func register() { _ = registered }

    static func displayFileName(forLanguage language: String) -> String {
        switch language {
        case "he", "iw": AppTokens.FontFile.hebrew
        case "ar": AppTokens.FontFile.arabic
        case "ru", "uk", "be", "bg", "sr", "kk": AppTokens.FontFile.cyrillic
        default: AppTokens.FontFile.latin
        }
    }

    /// PostScript names of the bundled files.
    static func postScriptName(forFile file: String) -> String {
        switch file {
        case AppTokens.FontFile.hebrew: "FrankRuhlLibre-Bold"
        case AppTokens.FontFile.arabic: "NotoNaskhArabic-SemiBold"
        case AppTokens.FontFile.cyrillic: "Lora-SemiBold"
        default: "Fraunces72ptSuperSoft-SemiBold"
        }
    }

    public static func display(size: CGFloat, relativeTo style: Font.TextStyle) -> Font {
        register()
        let language = Locale.current.language.languageCode?.identifier ?? "en"
        let name = postScriptName(forFile: displayFileName(forLanguage: language))
        if UIFont(name: name, size: size) != nil {
            return .custom(name, size: size, relativeTo: style)
        }
        return .system(style, design: .serif).weight(.semibold)
    }
}

// MARK: - Typography

public enum AppTextStyle {
    case displayLarge, displayMedium, title, headline, body, bodyStrong, callout, caption, overline, tab

    var spec: AppTypeSpec {
        switch self {
        case .displayLarge: AppTokens.TypeScale.displayLarge
        case .displayMedium: AppTokens.TypeScale.displayMedium
        case .title: AppTokens.TypeScale.title
        case .headline: AppTokens.TypeScale.headline
        case .body: AppTokens.TypeScale.body
        case .bodyStrong: AppTokens.TypeScale.bodyStrong
        case .callout: AppTokens.TypeScale.callout
        case .caption: AppTokens.TypeScale.caption
        case .overline: AppTokens.TypeScale.overline
        case .tab: AppTokens.TypeScale.tab
        }
    }

    var relativeStyle: Font.TextStyle {
        switch self {
        case .displayLarge: .largeTitle
        case .displayMedium: .title
        case .title: .title3
        case .headline: .headline
        case .body, .bodyStrong: .body
        case .callout: .callout
        case .caption: .footnote
        case .overline, .tab: .caption2
        }
    }

    public var font: Font {
        let spec = self.spec
        if spec.isDisplay { return AppFonts.display(size: spec.size, relativeTo: relativeStyle) }
        return .system(size: spec.size, weight: spec.weight).leading(.standard)
    }
}

public extension View {
    /// Applies a token text style: font, tracking and line spacing.
    func textStyle(_ style: AppTextStyle) -> some View {
        let spec = style.spec
        return self
            .font(style.font)
            .tracking(spec.tracking)
            .lineSpacing(max(0, spec.lineHeight - spec.size * 1.2))
    }
}

// MARK: - Spacing

public enum Space {
    public static let xxs = AppTokens.Space.xxs
    public static let xs = AppTokens.Space.xs
    public static let s = AppTokens.Space.s
    public static let m = AppTokens.Space.m
    public static let l = AppTokens.Space.l
    public static let xl = AppTokens.Space.xl
    public static let xxl = AppTokens.Space.xxl
}

public enum Radius {
    public static let s = AppTokens.Radius.s
    public static let m = AppTokens.Radius.m
    public static let l = AppTokens.Radius.l
}

public extension Animation {
    static let appSpring = Animation.spring(response: AppTokens.Motion.springResponse, dampingFraction: AppTokens.Motion.springDamping)
    static let appBouncy = Animation.spring(response: AppTokens.Motion.bouncyResponse, dampingFraction: AppTokens.Motion.bouncyDamping)
}
