package com.thinkofme.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.thinkofme.core.designsystem.theme.AppRadius
import com.thinkofme.core.designsystem.theme.AppTheme

/** Quiet text field: label above, soft filled surface, no outline. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = AppTheme.type.caption.copy(fontWeight = AppTheme.type.bodyStrong.fontWeight), color = colors.inkMuted)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(AppRadius.s))
                .background(colors.surface)
                .border(1.dp, colors.hairline, RoundedCornerShape(AppRadius.s))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
                textStyle = AppTheme.type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = AppTheme.type.body, color = colors.inkMuted.copy(alpha = 0.7f))
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
    }
}

/** Selectable chip used for priorities, circles and kinds. */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = AppTheme.colors
    val background by animateColorAsState(if (selected) colors.ink else colors.surface, label = "chip")
    val content by animateColorAsState(if (selected) colors.canvas else colors.ink, label = "chipText")
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, if (selected) background else colors.hairline, CircleShape)
            .pressable(onClick = onClick, role = Role.Checkbox)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(text, style = AppTheme.type.callout, color = content)
    }
}
