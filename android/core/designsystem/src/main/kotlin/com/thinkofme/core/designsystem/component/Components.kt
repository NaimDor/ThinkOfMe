package com.thinkofme.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.thinkofme.core.designsystem.theme.AppRadius
import com.thinkofme.core.designsystem.theme.AppSpace
import com.thinkofme.core.designsystem.theme.AppTheme
import com.thinkofme.core.designsystem.theme.personHue

/** Presses shrink slightly with a spring, for a tactile feel without 3D chrome. */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f, dampingRatio = 0.7f), label = "press")
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClickLabel = onClickLabel, onClick = onClick)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    container: Color = AppTheme.colors.accent,
    content: Color = AppTheme.colors.onAccent,
) {
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(AppRadius.m))
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .pressable(onClick = onClick, enabled = enabled && !loading)
            .padding(horizontal = AppSpace.l),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Box(Modifier.size(AppSpace.xs))
            }
            Text(text, style = AppTheme.type.bodyStrong, color = content, maxLines = 1)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: ImageVector? = null, enabled: Boolean = true) {
    PrimaryButton(
        text = text, onClick = onClick, modifier = modifier, leadingIcon = leadingIcon, enabled = enabled,
        container = AppTheme.colors.surfaceMuted, content = AppTheme.colors.ink,
    )
}

/** Small filled pill, e.g. "View list". */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(AppTheme.colors.accent)
            .pressable(onClick)
            .padding(horizontal = AppSpace.m, vertical = 10.dp),
    ) {
        Text(text, style = AppTheme.type.callout.copy(fontWeight = AppTheme.type.bodyStrong.fontWeight), color = AppTheme.colors.onAccent)
    }
}

@Composable
fun IconCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(AppTheme.colors.surfaceMuted)
            .pressable(onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.callout.copy(fontWeight = AppTheme.type.bodyStrong.fontWeight),
        color = AppTheme.colors.accentText,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

/** Initial-in-a-circle avatar in the person's signature hue. */
@Composable
fun InitialAvatar(initial: String, hueIndex: Int, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(personHue(hueIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            color = Color.White,
            style = AppTheme.type.bodyStrong.copy(fontSize = AppTheme.type.bodyStrong.fontSize * (size.value / 46f)),
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = AppSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = AppTheme.colors.hairline)
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 7.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/**
 * Days-left ring. Draws in from empty when first shown; the arc grows as the
 * date approaches (a full ring = today).
 */
@Composable
fun CountdownRing(
    days: Long,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    windowDays: Int = 30,
    accessibilityLabel: String,
) {
    val colors = AppTheme.colors
    val target = (1f - (days.coerceIn(0, windowDays.toLong()).toFloat() / windowDays)).coerceIn(0.04f, 1f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, tween(1200, easing = FastOutSlowInEasing)) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(modifier.size(size).semantics { contentDescription = accessibilityLabel }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(colors.accentTrack, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = 360f * progress.value
            drawArc(colors.accent, -90f, if (rtl) -sweep else sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(days.toString(), style = AppTheme.type.displayMedium.copy(fontSize = AppTheme.type.displayMedium.fontSize * 0.92f), color = colors.ink)
            Text(label, style = AppTheme.type.tab, color = colors.inkMuted)
        }
    }
}

/** A row of list content with consistent padding and an optional trailing slot. */
@Composable
fun ListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AppTheme.type.bodyStrong, color = AppTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = subtitle)
            }
        }
        trailing?.invoke()
    }
}

/** Calm empty state: a short title, one line of help and an optional action. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = AppSpace.xxl, horizontal = AppSpace.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpace.xs),
    ) {
        GiftBoxMoment(Modifier.size(96.dp))
        Text(title, style = AppTheme.type.title, color = AppTheme.colors.ink)
        Text(body, style = AppTheme.type.callout, color = AppTheme.colors.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Box(Modifier.padding(top = AppSpace.s)) { action() }
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = AppSpace.l)

