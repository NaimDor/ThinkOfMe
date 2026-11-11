package com.thinkofme.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The app's line icon set (24×24, round caps), defined from SVG path data so
 * iOS and Android draw identical glyphs. Icons that point in a reading
 * direction are flagged `autoMirror` and flip in right-to-left layouts.
 */
object AppIcons {
    private fun icon(name: String, vararg paths: String, stroke: Float = 1.8f, mirror: Boolean = false): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = mirror)
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = PathParser().parsePathString(d).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = stroke,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()

    private fun filled(name: String, d: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
            .build()

    val Home = icon("home", "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val Heart = icon("heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val HeartFilled = filled("heartFilled", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val People = icon("people", "M12.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0", "M2.5 20c.8-3.5 3.4-5.5 6.5-5.5s5.7 2 6.5 5.5", "M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.8c1.8.7 3 2.5 3.5 5.2")
    val Calendar = icon("calendar", "M6.5 5h11a3 3 0 0 1 3 3v9.5a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M3.5 10h17M8 3v4M16 3v4")
    val Plus = icon("plus", "M12 5v14M5 12h14", stroke = 2.2f)
    val Bell = icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20a2 2 0 0 0 4 0")
    val Back = icon("back", "M15 5l-7 7 7 7", stroke = 2f, mirror = true)
    val Forward = icon("forward", "M9 5l7 7-7 7", stroke = 2f, mirror = true)
    val Share = icon("share", "M12 3v12M7 8l5-5 5 5", "M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6", stroke = 2f)
    val External = icon("external", "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5", stroke = 2f, mirror = true)
    val Lock = icon("lock", "M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 18v-5a2.5 2.5 0 0 1 2.5-2.5z", "M8 10.5V8a4 4 0 0 1 8 0v2.5", stroke = 2f)
    val Gift = icon("gift", "M4.5 8h15a1 1 0 0 1 1 1v2.5a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", "M5 12.5V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7.5M12 8v13", "M12 8S10.5 3.5 8 4s-1 4 4 4zm0 0s1.5-4.5 4-4 1 4-4 4z", stroke = 2f)
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5", stroke = 2.4f)
    val Sparkle = filled("sparkle", "M12 2l2 8 8 2-8 2-2 8-2-8-8-2 8-2z")
    val Trash = icon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2")
    val Edit = icon("edit", "M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z", "M13.5 6.5l4 4")
    val Link = icon("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    val Settings = icon("settings", "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0", "M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.1a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z", stroke = 1.6f)
    val Close = icon("close", "M6 6l12 12M18 6L6 18", stroke = 2f)
    val Headphones = icon("headphones", "M4 15v-3a8 8 0 0 1 16 0v3", "M4.8 14h.9a1.8 1.8 0 0 1 1.8 1.8v2.9a1.8 1.8 0 0 1-1.8 1.8h-.9A1.8 1.8 0 0 1 3 18.7v-2.9A1.8 1.8 0 0 1 4.8 14z", "M18.3 14h.9a1.8 1.8 0 0 1 1.8 1.8v2.9a1.8 1.8 0 0 1-1.8 1.8h-.9a1.8 1.8 0 0 1-1.8-1.8v-2.9a1.8 1.8 0 0 1 1.8-1.8z")
    val Idea = icon("idea", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z")
    val Photo = icon("photo", "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M4 16l4.5-4.5 3.5 3.5 2.5-2.5L20 18", "M16 9.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 1 1 3 0")
    val More = filled("more", "M6 12a1.6 1.6 0 1 1-3.2 0 1.6 1.6 0 1 1 3.2 0zm7.6 0a1.6 1.6 0 1 1-3.2 0 1.6 1.6 0 1 1 3.2 0zm7.6 0a1.6 1.6 0 1 1-3.2 0 1.6 1.6 0 1 1 3.2 0z")
}
