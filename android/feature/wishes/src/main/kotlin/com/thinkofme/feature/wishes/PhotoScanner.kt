package com.thinkofme.feature.wishes

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.thinkofme.core.designsystem.icon.AppIcons
import com.thinkofme.core.designsystem.theme.AppSpace
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.ui.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

private val scanSteps = listOf(R.string.scan_looking, R.string.scan_brand, R.string.scan_price, R.string.scan_store)

/** Decodes the picked JPEG once for the scanner card. */
@Composable
fun rememberPhotoBitmap(jpeg: ByteArray): ImageBitmap? = remember(jpeg) {
    runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap() }.getOrNull()
}

/**
 * While a photo is being read: the photo with a magnifying glass gliding over it in a
 * figure-8, the lens showing the photo zoomed in, and a line saying what it looks for.
 *
 * [phase] pins the animation to one frame (screenshots).
 */
@Composable
fun PhotoScanner(image: ImageBitmap, modifier: Modifier = Modifier, phase: Float? = null) {
    val context = LocalContext.current
    val reduceMotion = remember { isReduceMotion(context) }
    val transition = rememberInfiniteTransition(label = "scan")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_800, easing = LinearEasing), RepeatMode.Restart),
        label = "lens",
    )
    // A pinned phase shows one frame of the full effect; reduced motion shows a calm, still lens.
    val settled = phase != null || reduceMotion
    val still = phase == null && reduceMotion
    val t = phase ?: if (reduceMotion) 0.12f else animated

    // The lens drops onto the photo with a spring; the photo dims as it lands.
    val arrive = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(Unit) { if (!settled) arrive.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 180f)) }

    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(phase) {
        if (phase != null) return@LaunchedEffect
        while (true) {
            delay(1_600)
            step = (step + 1) % scanSteps.size
        }
    }
    val description = stringResource(R.string.reading_photo)

    Column(modifier.semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(AppSpace.s)) {
        val ring = AppTheme.colors.surface
        val accent = AppTheme.colors.accent
        val gold = Color(0xFFF9DE9C)
        Canvas(
            Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(24.dp)),
        ) {
            val a = arrive.value
            drawCropped(image)
            drawRect(Color.Black.copy(alpha = 0.30f * a.coerceIn(0f, 1f)))

            // Figure-8 path across the photo.
            fun at(phase: Float): Offset {
                val angle = 2 * PI * phase
                return Offset(
                    x = size.width * (0.5f + 0.31f * sin(angle).toFloat()),
                    y = size.height * (0.5f + 0.19f * sin(2 * angle).toFloat()),
                )
            }
            val center = at(t)
            val breathe = if (still) 0f else sin(2 * PI * t * 3).toFloat()
            val radius = size.height * 0.29f * a * (1f + 0.035f * breathe)

            // Sparkles left where the lens has just looked.
            if (!still) {
                for (i in 0 until 5) {
                    val spot = i / 5f + 0.04f
                    val since = ((t - spot) % 1f + 1f) % 1f
                    if (since < 0.22f) {
                        val glow = sin(PI * since / 0.22f).toFloat()
                        val drift = Offset(size.height * 0.10f * (if (i % 2 == 0) 1f else -1f), -size.height * (0.10f + 0.10f * since))
                        drawSparkle(at(spot) + drift, size.height * 0.055f * (0.5f + 0.5f * glow), gold, glow * 0.95f, rotation = 90f * since)
                    }
                }
            }

            // A soft trail and a pulsing halo, so the lens reads as searching.
            if (!still) {
                for (k in 3 downTo 1) {
                    drawCircle(Color.White, radius * (1f - 0.06f * k), at(t - 0.018f * k), alpha = 0.07f / k)
                }
                val pulse = (t * 4f) % 1f
                drawCircle(gold, radius * (1.08f + 0.42f * pulse), center, alpha = 0.45f * (1f - pulse), style = Stroke(width = radius * 0.05f))
            }

            // Soft shadow so the lens floats above the photo.
            drawCircle(Color.Black.copy(alpha = 0.24f), radius * 1.07f, center + Offset(radius * 0.10f, radius * 0.15f))

            // Zoomed photo inside the lens.
            val lens = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
            clipPath(lens) {
                scale(2f, pivot = center) { drawCropped(image) }
                drawRect(Color.White.copy(alpha = 0.06f))
            }

            // Handle, ring and a glint that slides round the glass.
            val handleStart = center + Offset(radius * 0.72f, radius * 0.72f)
            val handleEnd = center + Offset(radius * 1.32f, radius * 1.32f)
            drawLine(ring, handleStart, handleEnd, strokeWidth = radius * 0.22f, cap = StrokeCap.Round)
            drawLine(accent, handleStart + Offset(radius * 0.18f, radius * 0.18f), handleEnd, strokeWidth = radius * 0.14f, cap = StrokeCap.Round)
            drawCircle(ring, radius, center, style = Stroke(width = radius * 0.13f))
            drawArc(
                Color.White.copy(alpha = 0.75f), startAngle = 200f + 24f * breathe, sweepAngle = 50f, useCenter = false,
                topLeft = center - Offset(radius * 0.7f, radius * 0.7f), size = Size(radius * 1.4f, radius * 1.4f),
                style = Stroke(width = radius * 0.07f, cap = StrokeCap.Round),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The sparkle beats while it works; each line rolls up to make room for the next.
            val beat = if (still) 1f else 1f + 0.18f * sin(2 * PI * t * 4).toFloat()
            Icon(AppIcons.Sparkle, null, tint = AppTheme.colors.accentText, modifier = Modifier.size(16.dp).graphicsLayer { scaleX = beat; scaleY = beat })
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(320, delayMillis = 80)) + slideInVertically(tween(380)) { it / 2 }) togetherWith
                        (fadeOut(tween(200)) + slideOutVertically(tween(300)) { -it / 2 })
                },
                label = "step",
            ) { index ->
                Text(stringResource(scanSteps[index]), style = AppTheme.type.callout, color = AppTheme.colors.inkMuted)
            }
        }
    }
}

/** A four-point sparkle centred on [center]. */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color, alpha: Float, rotation: Float) {
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

/** Draws [image] filling the canvas like ContentScale.Crop. */
private fun DrawScope.drawCropped(image: ImageBitmap) {
    val scale = max(size.width / image.width, size.height / image.height)
    val srcW = (size.width / scale).toInt().coerceAtMost(image.width)
    val srcH = (size.height / scale).toInt().coerceAtMost(image.height)
    drawImage(
        image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
    )
}

/** "Remove animations" in accessibility settings sets the animator scale to 0. */
private fun isReduceMotion(context: android.content.Context): Boolean {
    val resolver = context.contentResolver
    return runCatching {
        android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}
