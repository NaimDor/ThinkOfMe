package com.thinkofme.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.thinkofme.core.designsystem.icon.AppIcons
import com.thinkofme.core.designsystem.theme.AppRadius
import com.thinkofme.core.designsystem.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** True when the user turned animations off system-wide. Moments become simple fades. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The "wrap it" moment. When [wrapped] turns true the stage lifts toward you and
 * rocks in 3D on one continuous curve, a gold ribbon sweeps around it, the bow
 * drops on with a springy wobble, a glint runs along the ribbon and sparkles
 * burst in two waves, with haptics. At rest the stage is flat; a wrapped stage
 * keeps its ribbon.
 */
@Composable
fun WrapStage(
    wrapped: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val lift = remember { Animatable(0f) }
    val vertical = remember { Animatable(if (wrapped) 1f else 0f) }
    val horizontal = remember { Animatable(if (wrapped) 1f else 0f) }
    val bow = remember { Animatable(if (wrapped) 1f else 0f) }
    val shine = remember { Animatable(0f) }
    var burst by remember { mutableIntStateOf(0) }
    var previous by remember { mutableIntStateOf(if (wrapped) 1 else 0) }

    LaunchedEffect(wrapped) {
        val now = if (wrapped) 1 else 0
        if (now == previous) return@LaunchedEffect
        previous = now
        if (reduceMotion) {
            vertical.snapTo(now.toFloat()); horizontal.snapTo(now.toFloat()); bow.snapTo(now.toFloat())
            return@LaunchedEffect
        }
        if (wrapped) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            launch {
                lift.snapTo(0f)
                lift.animateTo(1f, tween(1250, easing = LinearEasing))
            }
            launch { delay(120); vertical.animateTo(1f, tween(620, easing = RibbonEasing)) }
            launch { delay(300); horizontal.animateTo(1f, tween(620, easing = RibbonEasing)) }
            launch {
                delay(720)
                bow.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = 260f))
            }
            launch {
                delay(840)
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                burst++
            }
            launch {
                shine.snapTo(0f)
                delay(980)
                shine.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
        } else {
            launch { bow.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
            launch { delay(60); horizontal.animateTo(0f, tween(340, easing = RibbonEasing)) }
            launch { delay(160); vertical.animateTo(0f, tween(340, easing = RibbonEasing)) }
        }
    }

    // One continuous curve: rock back, swing forward a little, settle flat (a damped sine).
    val t = lift.value.coerceIn(0f, 1f)
    val damp = (1f - t) * kotlin.math.sqrt(1f - t)
    val tiltX = 30f * sin(2.0 * Math.PI * t).toFloat() * damp
    val rise = sin(Math.PI * t).toFloat()

    Box(
        modifier.graphicsLayer {
            rotationX = tiltX
            rotationY = tiltX * -0.55f
            scaleX = 1f + 0.055f * rise
            scaleY = 1f + 0.055f * rise
            translationY = -10.dp.toPx() * rise
            cameraDistance = 12f * density
        },
    ) {
        content()

        // Ribbons and the glint that runs along them once the bow is on.
        Canvas(Modifier.fillMaxSize()) {
            val band = 32.dp.toPx()
            val cx = (size.width - band) / 2
            val cy = (size.height - band) / 2
            val v = vertical.value
            val h = horizontal.value
            if (v > 0f) {
                drawRect(ribbonBrushVertical(cx, band), topLeft = Offset(cx, 0f), size = Size(band, size.height * v))
            }
            if (h > 0f) {
                val width = size.width * h
                drawRect(ribbonBrushHorizontal(cy, band), topLeft = Offset(if (rtl) size.width - width else 0f, cy), size = Size(width, band))
            }
            val s = shine.value
            if (s > 0f && s < 1f) {
                val glint = 120.dp.toPx()
                val fade = sin(Math.PI * s).toFloat()
                val y = -glint + (size.height + glint) * s
                clipRect(cx, 0f, cx + band, size.height) {
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.75f * fade), Color.Transparent), startY = y, endY = y + glint),
                        topLeft = Offset(cx, y), size = Size(band, glint),
                    )
                }
                val x = if (rtl) size.width - (size.width + glint) * s else -glint + (size.width + glint) * s
                clipRect(0f, cy, size.width, cy + band) {
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.75f * fade), Color.Transparent), startX = x, endX = x + glint),
                        topLeft = Offset(x, cy), size = Size(glint, band),
                    )
                }
            }
        }
        Canvas(
            Modifier
                .align(Alignment.Center)
                .offset(y = (-22).dp)
                .size(width = 124.dp, height = 72.dp)
                .graphicsLayer {
                    val b = bow.value
                    scaleX = b; scaleY = b
                    rotationZ = (1f - b) * -32f
                    translationY = (1f - b.coerceAtMost(1f)) * -36.dp.toPx()
                    alpha = (b * 3f).coerceIn(0f, 1f)
                },
        ) { drawBow() }

        if (burst > 0) SparkleBurst(key = burst, modifier = Modifier.align(Alignment.Center))
    }
}

/** Slow start, long glide: the ribbon is pulled around the gift. */
private val RibbonEasing = CubicBezierEasing(0.5f, 0f, 0.15f, 1f)

private val ribbonColors = listOf(Color(0xFFC98E2F), Color(0xFFF9DE9C), Color(0xFFF2C46D), Color(0xFFD79B3A))
private fun ribbonBrushVertical(left: Float, band: Float) = Brush.horizontalGradient(ribbonColors, startX = left, endX = left + band)
private fun ribbonBrushHorizontal(top: Float, band: Float) = Brush.verticalGradient(ribbonColors, startY = top, endY = top + band)

private fun DrawScope.drawBow() {
    val w = size.width / 128f
    val h = size.height / 74f
    fun p(x: Float, y: Float) = Offset(x * w, y * h)
    val gold = Brush.linearGradient(listOf(Color(0xFFFBE3A8), Color(0xFFF2C46D), Color(0xFFC98E2F)), p(0f, 0f), p(128f, 74f))
    val left = Path().apply {
        moveTo(p(64f, 50f).x, p(64f, 50f).y)
        cubicTo(p(40f, 8f).x, p(40f, 8f).y, p(6f, 10f).x, p(6f, 10f).y, p(10f, 38f).x, p(10f, 38f).y)
        cubicTo(p(13f, 58f).x, p(13f, 58f).y, p(44f, 58f).x, p(44f, 58f).y, p(64f, 50f).x, p(64f, 50f).y)
        close()
    }
    val right = Path().apply {
        moveTo(p(64f, 50f).x, p(64f, 50f).y)
        cubicTo(p(88f, 8f).x, p(88f, 8f).y, p(122f, 10f).x, p(122f, 10f).y, p(118f, 38f).x, p(118f, 38f).y)
        cubicTo(p(115f, 58f).x, p(115f, 58f).y, p(84f, 58f).x, p(84f, 58f).y, p(64f, 50f).x, p(64f, 50f).y)
        close()
    }
    val tails = Path().apply {
        moveTo(p(58f, 52f).x, p(58f, 52f).y); lineTo(p(44f, 74f).x, p(44f, 74f).y); lineTo(p(56f, 74f).x, p(56f, 74f).y)
        lineTo(p(64f, 62f).x, p(64f, 62f).y); lineTo(p(72f, 74f).x, p(72f, 74f).y); lineTo(p(84f, 74f).x, p(84f, 74f).y)
        lineTo(p(70f, 52f).x, p(70f, 52f).y); close()
    }
    drawPath(tails, Color(0xFFD79B3A))
    drawPath(left, gold)
    drawPath(right, gold)
    drawOval(Color(0xFFE3A94A), topLeft = p(53f, 38f), size = Size(22f * w, 20f * h))
}

/** A four-point sparkle centred on [center]. */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color, alpha: Float = 1f, rotation: Float = 0f) {
    if (alpha <= 0f || radius <= 0f) return
    val pinch = radius * 0.22f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + pinch, center.y - pinch, center.x + radius, center.y)
        quadraticTo(center.x + pinch, center.y + pinch, center.x, center.y + radius)
        quadraticTo(center.x - pinch, center.y + pinch, center.x - radius, center.y)
        quadraticTo(center.x - pinch, center.y - pinch, center.x, center.y - radius)
        close()
    }
    rotate(rotation, center) { drawPath(path, color, alpha = alpha.coerceIn(0f, 1f)) }
}

private class Spark(val angle: Double, val distance: Float, val size: Float, val delay: Float, val color: Int)

/** Two waves of sparkles flying out from the bow, with a soft ring of light. */
@Composable
private fun SparkleBurst(key: Int, modifier: Modifier = Modifier) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(1500, easing = LinearEasing)) }
    val accent = AppTheme.colors.accent
    val palette = listOf(Color(0xFFE3A94A), Color(0xFFF9DE9C), accent, Color.White)
    val sparks = remember {
        // A wide first wave, then a closer, slower second one.
        List(9) { i -> Spark(Math.toRadians(-90.0 + i * 40.0 + (i % 2) * 9.0), 150f + (i % 3) * 22f, 9f + (i % 3) * 3f, 0f, i % 4) } +
            List(7) { i -> Spark(Math.toRadians(-64.0 + i * 51.0), 86f + (i % 2) * 18f, 6f + (i % 2) * 3f, 0.16f, (i + 1) % 4) }
    }
    Canvas(modifier.size(1.dp)) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        // Ring of light.
        val ring = (p / 0.55f).coerceIn(0f, 1f)
        val ringEase = 1f - (1f - ring) * (1f - ring)
        drawCircle(Color(0xFFF9DE9C), radius = (30f + 150f * ringEase).dp.toPx(), center = center, alpha = 0.55f * (1f - ring), style = Stroke(width = (10f * (1f - ring) + 1f).dp.toPx()))
        sparks.forEach { spark ->
            val local = ((p - spark.delay) / (1f - spark.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val out = 1f - (1f - local) * (1f - local) * (1f - local)
            val x = (cos(spark.angle) * spark.distance * out).toFloat().dp.toPx()
            val y = (sin(spark.angle) * spark.distance * out).toFloat().dp.toPx() + 46.dp.toPx() * local * local
            val grow = (local / 0.18f).coerceAtMost(1f)
            val fade = if (local < 0.55f) 1f else 1f - (local - 0.55f) / 0.45f
            drawSparkle(center + Offset(x, y), spark.size.dp.toPx() * grow * (0.7f + 0.3f * fade), palette[spark.color], fade, rotation = 140f * local)
        }
    }
}

/**
 * A small gift box that floats, turns in 3D and twinkles. Used for empty states,
 * onboarding and sign in: the "moment" outside of wrapping.
 */
@Composable
fun GiftBoxMoment(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "gift")
    // One looping phase drives everything, so the turn, the float and the twinkles stay in step.
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val accent = AppTheme.colors.accent
    val lid = accent.copy(red = (accent.red * 1.08f).coerceAtMost(1f))
    val still = reduceMotion
    val turn = if (still) 0f else sin(2 * Math.PI * phase).toFloat()
    val float = if (still) 0f else sin(4 * Math.PI * phase).toFloat()

    Box(modifier, contentAlignment = Alignment.Center) {
        // The shadow stays on the ground and tightens as the box rises.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val spread = 1f - 0.16f * float
            drawOval(
                Color.Black.copy(alpha = 0.10f - 0.03f * float),
                topLeft = Offset(w * (0.5f - 0.28f * spread), h * 0.87f), size = Size(w * 0.56f * spread, h * 0.075f),
            )
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = turn * 26f
                    rotationZ = turn * -3f
                    translationY = -float * 7f * density - 2f * density
                    scaleY = 1f + 0.018f * float
                    cameraDistance = 10f * density
                },
        ) {
            val w = size.width
            val h = size.height
            // body
            drawRoundRect(accent, topLeft = Offset(w * 0.24f, h * 0.42f), size = Size(w * 0.52f, h * 0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
            // lid
            drawRoundRect(lid, topLeft = Offset(w * 0.19f, h * 0.32f), size = Size(w * 0.62f, h * 0.13f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
            // ribbon
            drawRect(Color(0xFFF2C46D), topLeft = Offset(w * 0.46f, h * 0.32f), size = Size(w * 0.08f, h * 0.5f))
            // bow
            val bow = Path().apply {
                moveTo(w * 0.5f, h * 0.32f)
                cubicTo(w * 0.36f, h * 0.1f, w * 0.2f, h * 0.2f, w * 0.3f, h * 0.29f)
                cubicTo(w * 0.35f, h * 0.33f, w * 0.45f, h * 0.33f, w * 0.5f, h * 0.32f)
                moveTo(w * 0.5f, h * 0.32f)
                cubicTo(w * 0.64f, h * 0.1f, w * 0.8f, h * 0.2f, w * 0.7f, h * 0.29f)
                cubicTo(w * 0.65f, h * 0.33f, w * 0.55f, h * 0.33f, w * 0.5f, h * 0.32f)
            }
            drawPath(bow, Color(0xFFE3A94A))
        }
        // Twinkles around the box, each on its own beat.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val spots = listOf(Triple(0.16f, 0.24f, 0.00f), Triple(0.86f, 0.36f, 0.37f), Triple(0.78f, 0.10f, 0.68f))
            spots.forEach { (x, y, offset) ->
                val beat = if (still) 0.5f else ((phase * 2f + offset) % 1f)
                val glow = sin(Math.PI * beat).toFloat()
                drawSparkle(Offset(w * x, h * y), w * 0.055f * (0.4f + 0.6f * glow), Color(0xFFE3A94A), alpha = glow, rotation = 45f * beat)
            }
        }
    }
}

/** Rounded media tile used where a product photo goes; tinted placeholder when none. */
@Composable
fun MediaTile(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(AppRadius.l)).background(AppTheme.colors.surfaceMuted),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
