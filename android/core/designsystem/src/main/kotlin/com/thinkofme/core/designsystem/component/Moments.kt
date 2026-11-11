package com.thinkofme.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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

private val ribbonBrushVertical = Brush.horizontalGradient(listOf(Color(0xFFC98E2F), Color(0xFFF9DE9C), Color(0xFFF2C46D), Color(0xFFD79B3A)))
private val ribbonBrushHorizontal = Brush.verticalGradient(listOf(Color(0xFFC98E2F), Color(0xFFF9DE9C), Color(0xFFF2C46D), Color(0xFFD79B3A)))

/**
 * The "wrap it" moment. When [wrapped] turns true the stage lifts and tilts
 * in 3D, a gold ribbon wraps it, the bow springs on and sparkles burst, with
 * a haptic tick. At rest the stage is flat; a wrapped stage keeps its ribbon.
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
                lift.animateTo(1f, keyframes { durationMillis = 1000 })
            }
            launch { delay(150); vertical.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            launch { delay(350); horizontal.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            launch {
                delay(700)
                bow.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f))
            }
            launch {
                delay(820)
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                burst++
            }
        } else {
            launch { bow.animateTo(0f, tween(180)) }
            launch { horizontal.animateTo(0f, tween(260)) }
            launch { delay(120); vertical.animateTo(0f, tween(260)) }
        }
    }

    // 0→1 over the lift: tilt back, overshoot forward, settle flat.
    val t = lift.value
    val tiltX = when {
        t <= 0f || t >= 1f -> 0f
        t < 0.3f -> 16f * (t / 0.3f)
        t < 0.65f -> 16f - 21f * ((t - 0.3f) / 0.35f)
        else -> -5f * (1f - (t - 0.65f) / 0.35f)
    }
    val scale = 1f - 0.05f * sin(Math.PI * t.coerceIn(0f, 1f)).toFloat()

    Box(
        modifier.graphicsLayer {
            rotationX = tiltX
            rotationY = tiltX * -0.5f
            scaleX = scale
            scaleY = scale
            cameraDistance = 14f * density
        },
    ) {
        content()

        Box(
            Modifier
                .align(Alignment.Center)
                .width(32.dp)
                .fillMaxHeight()
                .graphicsLayer { scaleY = vertical.value; transformOrigin = TransformOrigin(0.5f, 0f) }
                .background(ribbonBrushVertical),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(32.dp)
                .graphicsLayer { scaleX = horizontal.value; transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f) }
                .background(ribbonBrushHorizontal),
        )
        Canvas(
            Modifier
                .align(Alignment.Center)
                .offset(y = (-22).dp)
                .size(width = 116.dp, height = 68.dp)
                .graphicsLayer { scaleX = bow.value; scaleY = bow.value; rotationZ = (1f - bow.value) * -25f },
        ) { drawBow() }

        if (burst > 0) SparkleBurst(key = burst, modifier = Modifier.align(Alignment.Center))
    }
}

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

@Composable
private fun SparkleBurst(key: Int, modifier: Modifier = Modifier) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val p = progress.value
    val colors = listOf(Color(0xFFE3A94A), Color(0xFFE3A94A), AppTheme.colors.accent, AppTheme.colors.accent, Color.White, Color(0xFFF2C46D))
    Box(modifier) {
        colors.forEachIndexed { index, color ->
            val angle = Math.toRadians(-140.0 + index * 52.0)
            val distance = 120f * p
            Icon(
                AppIcons.Sparkle,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        translationX = (cos(angle) * distance).toFloat() * density
                        translationY = (sin(angle) * distance).toFloat() * density
                        rotationZ = 90f * p
                        alpha = if (p < 0.7f) 1f else (1f - (p - 0.7f) / 0.3f)
                        scaleX = p.coerceAtMost(1f); scaleY = p.coerceAtMost(1f)
                    },
            )
        }
    }
}

/**
 * A small gift box that floats and turns in 3D. Used for empty states,
 * onboarding and pull-to-refresh: the "moment" outside of wrapping.
 */
@Composable
fun GiftBoxMoment(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "gift")
    val turn by transition.animateFloat(-22f, 22f, infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "turn")
    val float by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "float")
    val accent = AppTheme.colors.accent
    val lid = accent.copy(red = (accent.red * 1.08f).coerceAtMost(1f))

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reduceMotion) {
                        rotationY = turn
                        translationY = (sin(float * 2 * Math.PI) * 4f).toFloat() * density
                    }
                    cameraDistance = 10f * density
                },
        ) {
            val w = size.width
            val h = size.height
            // shadow
            drawOval(Color.Black.copy(alpha = 0.08f), topLeft = Offset(w * 0.22f, h * 0.86f), size = Size(w * 0.56f, h * 0.08f))
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
