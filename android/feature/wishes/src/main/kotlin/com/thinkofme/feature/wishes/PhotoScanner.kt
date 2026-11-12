package com.thinkofme.feature.wishes

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
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
 * [phase] pins the animation to one frame (screenshots, reduced motion).
 */
@Composable
fun PhotoScanner(image: ImageBitmap, modifier: Modifier = Modifier, phase: Float? = null) {
    val context = LocalContext.current
    val reduceMotion = remember { isReduceMotion(context) }
    val transition = rememberInfiniteTransition(label = "scan")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing), RepeatMode.Restart),
        label = "lens",
    )
    val t = phase ?: if (reduceMotion) 0.12f else animated

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
        Canvas(
            Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)),
        ) {
            drawCropped(image)
            drawRect(Color.Black.copy(alpha = 0.28f))

            // Figure-8 path across the photo.
            val angle = 2 * PI * t
            val center = Offset(
                x = size.width * (0.5f + 0.30f * sin(angle).toFloat()),
                y = size.height * (0.5f + 0.20f * sin(2 * angle).toFloat()),
            )
            val radius = size.height * 0.26f
            val lens = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }

            // Soft shadow so the lens floats above the photo.
            drawCircle(Color.Black.copy(alpha = 0.22f), radius * 1.06f, center + Offset(radius * 0.10f, radius * 0.14f))

            // Zoomed photo inside the lens.
            clipPath(lens) {
                scale(1.9f, pivot = center) { drawCropped(image) }
                drawRect(Color.White.copy(alpha = 0.06f))
            }

            // Handle, ring and a small glint.
            val handleStart = center + Offset(radius * 0.72f, radius * 0.72f)
            val handleEnd = center + Offset(radius * 1.32f, radius * 1.32f)
            drawLine(ring, handleStart, handleEnd, strokeWidth = radius * 0.22f, cap = StrokeCap.Round)
            drawLine(accent, handleStart + Offset(radius * 0.18f, radius * 0.18f), handleEnd, strokeWidth = radius * 0.14f, cap = StrokeCap.Round)
            drawCircle(ring, radius, center, style = Stroke(width = radius * 0.13f))
            drawArc(
                Color.White.copy(alpha = 0.7f), startAngle = 200f, sweepAngle = 50f, useCenter = false,
                topLeft = center - Offset(radius * 0.7f, radius * 0.7f), size = Size(radius * 1.4f, radius * 1.4f),
                style = Stroke(width = radius * 0.07f, cap = StrokeCap.Round),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Sparkle, null, tint = AppTheme.colors.accentText, modifier = Modifier.size(16.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "step",
            ) { index ->
                Text(stringResource(scanSteps[index]), style = AppTheme.type.callout, color = AppTheme.colors.inkMuted)
            }
        }
    }
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
