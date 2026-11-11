package com.thinkofme.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.core.os.ConfigurationCompat
import com.thinkofme.core.designsystem.R
import java.util.Locale

@Immutable
data class AppTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val callout: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val tab: TextStyle,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
val LocalAppTypography = staticCompositionLocalOf { appTypography(FontFamily.Serif) }

/** Entry point for design tokens inside composables: `AppTheme.colors.ink`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current
}

/**
 * The expressive display face differs per script: Fraunces for Latin,
 * Frank Ruhl Libre for Hebrew, Lora for Cyrillic and Noto Naskh for Arabic.
 * Body text uses the platform face for legibility and font scaling.
 */
fun displayFontFamily(locale: Locale): FontFamily = when (locale.language) {
    "he", "iw" -> FontFamily(Font(R.font.frankruhllibre_bold, FontWeight.Bold), Font(R.font.frankruhllibre_bold, FontWeight.SemiBold))
    "ar" -> FontFamily(Font(R.font.notonaskharabic_semibold, FontWeight.SemiBold))
    "ru", "uk", "be", "bg", "sr", "kk" -> FontFamily(Font(R.font.lora_semibold, FontWeight.SemiBold))
    else -> FontFamily(Font(R.font.fraunces_semibold, FontWeight.SemiBold))
}

private val tightLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun AppTypeSpec.toStyle(display: FontFamily): TextStyle = TextStyle(
    fontFamily = if (isDisplay) display else FontFamily.Default,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    lineHeightStyle = tightLineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

fun appTypography(display: FontFamily) = AppTypography(
    displayLarge = AppTypeScale.displayLarge.toStyle(display),
    displayMedium = AppTypeScale.displayMedium.toStyle(display),
    title = AppTypeScale.title.toStyle(display),
    headline = AppTypeScale.headline.toStyle(display),
    body = AppTypeScale.body.toStyle(display),
    bodyStrong = AppTypeScale.bodyStrong.toStyle(display),
    callout = AppTypeScale.callout.toStyle(display),
    caption = AppTypeScale.caption.toStyle(display),
    overline = AppTypeScale.overline.toStyle(display),
    tab = AppTypeScale.tab.toStyle(display),
)

private fun AppColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent, background = canvas, onBackground = ink,
        surface = surface, onSurface = ink, surfaceVariant = surfaceMuted, onSurfaceVariant = inkMuted,
        outline = hairline, outlineVariant = hairline, error = danger, secondary = ribbon, onSecondary = onRibbon,
        surfaceContainer = surface, surfaceContainerHigh = surfaceMuted, surfaceContainerLow = canvas,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent, background = canvas, onBackground = ink,
        surface = surface, onSurface = ink, surfaceVariant = surfaceMuted, onSurfaceVariant = inkMuted,
        outline = hairline, outlineVariant = hairline, error = danger, secondary = ribbon, onSecondary = onRibbon,
        surfaceContainer = surface, surfaceContainerHigh = surfaceMuted, surfaceContainerLow = canvas,
    )
}

@Composable
fun ThinkOfMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
    val typography = remember(locale.language) { appTypography(displayFontFamily(locale)) }
    val material = remember(colors) { colors.toMaterial() }

    CompositionLocalProvider(LocalAppColors provides colors, LocalAppTypography provides typography) {
        MaterialTheme(
            colorScheme = material,
            typography = MaterialTheme.typography.copy(
                bodyLarge = typography.body,
                bodyMedium = typography.callout,
                labelLarge = typography.bodyStrong,
                titleLarge = typography.title,
            ),
            content = content,
        )
    }
}

/** A person's signature color, stable for their id. */
fun personHue(index: Int): Color = PersonHues[Math.floorMod(index, PersonHues.size)]

fun hueIndexFor(id: String): Int = Math.floorMod(id.hashCode(), PersonHues.size)
