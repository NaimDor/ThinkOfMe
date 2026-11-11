import Testing
@testable import DesignSystem

@Suite struct TokensTests {
    @Test func everyScriptHasADisplayFont() {
        #expect(AppFonts.displayFileName(forLanguage: "he") == AppTokens.FontFile.hebrew)
        #expect(AppFonts.displayFileName(forLanguage: "ar") == AppTokens.FontFile.arabic)
        #expect(AppFonts.displayFileName(forLanguage: "ru") == AppTokens.FontFile.cyrillic)
        #expect(AppFonts.displayFileName(forLanguage: "en") == AppTokens.FontFile.latin)
    }

    @Test func personHueIsStableAndInRange() {
        #expect(PersonHue.index(for: "maya") == PersonHue.index(for: "maya"))
        #expect((0..<AppTokens.personHues.count).contains(PersonHue.index(for: "anyone")))
    }
}
