package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.pc.remote.PcText
import com.enaboapps.switchify.pc.remote.actions.PcActionIcon
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction
import kotlin.math.min

@Composable
fun PcText.resolve(): String = when (this) {
    is PcText.Res -> {
        val resolved = args.map { arg -> if (arg is PcText) arg.resolve() else arg }
        stringResource(id, *resolved.toTypedArray())
    }
    is PcText.Literal -> text
    is PcText.Joined -> parts.map { it.resolve() }.joinToString(separator)
}

fun PcActionIcon.vector(): ImageVector = when (this) {
    PcActionIcon.DoubleClick -> Icons.Rounded.AdsClick
    PcActionIcon.Mouse -> Icons.Rounded.Mouse
    PcActionIcon.Drag -> Icons.Rounded.PanTool
    PcActionIcon.ScrollUp -> Icons.Rounded.ArrowUpward
    PcActionIcon.ScrollDown -> Icons.Rounded.ArrowDownward
    PcActionIcon.Slower -> Icons.Rounded.Remove
    PcActionIcon.Faster -> Icons.Rounded.Add
    PcActionIcon.Warning -> Icons.Rounded.Warning
}

@Composable
fun PcActionControl(action: PcResolvedAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val presentation = action.presentation
    PcControlButton(
        label = presentation.label.resolve(),
        accessibilityLabel = presentation.accessibilityLabel?.resolve(),
        onClick = onClick,
        modifier = modifier,
        icon = presentation.icon?.vector(),
        checked = presentation.selected,
        enabled = action.enabled,
        danger = presentation.danger,
        emphasized = presentation.emphasized,
        keySize = presentation.keySize,
        stacked = true,
        shape = PcKeyShape
    )
}

val PcKeyShape: Shape = GenericShape { size, _ ->
    val width = size.width
    val height = size.height
    val radius = min(width, height) * 0.24f
    val handle = radius * 0.2f
    moveTo(radius, 0f)
    lineTo(width - radius, 0f)
    cubicTo(width - handle, 0f, width, handle, width, radius)
    lineTo(width, height - radius)
    cubicTo(width, height - handle, width - handle, height, width - radius, height)
    lineTo(radius, height)
    cubicTo(handle, height, 0f, height - handle, 0f, height - radius)
    lineTo(0f, radius)
    cubicTo(0f, handle, handle, 0f, radius, 0f)
    close()
}

@Composable
fun PcControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String? = null,
    icon: ImageVector? = null,
    selected: Boolean? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    emphasized: Boolean = false,
    keySize: Boolean = false,
    stacked: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium
) {
    val scheme = MaterialTheme.colorScheme
    val isSelected = selected == true || checked == true
    val container = when {
        isSelected -> scheme.primary
        emphasized -> scheme.primaryContainer
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        isSelected -> scheme.onPrimary
        danger -> scheme.error
        emphasized -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val border = when {
        danger -> scheme.error
        isSelected -> scheme.primary
        else -> scheme.outlineVariant
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .heightIn(min = if (keySize) 64.dp else 58.dp)
            .semantics {
                if (accessibilityLabel != null) contentDescription = accessibilityLabel
                if (selected != null) this.selected = selected
                if (checked != null) {
                    role = Role.Switch
                    toggleableState = ToggleableState(checked)
                }
            }
    ) {
        val inner = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = if (stacked) 8.dp else 12.dp, vertical = 8.dp)
        if (stacked) {
            Column(
                modifier = inner,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                ButtonContent(label, icon, isSelected, compact = true)
            }
        } else {
            Row(
                modifier = inner,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ButtonContent(label, icon, isSelected)
            }
        }
    }
}

@Composable
private fun ButtonContent(label: String, icon: ImageVector?, selected: Boolean, compact: Boolean = false) {
    if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
    Text(
        text = label,
        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        maxLines = if (compact) 3 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis
    )
    if (selected) {
        Icon(imageVector = Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}
