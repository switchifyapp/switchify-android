package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.pc.remote.PcText
import com.enaboapps.switchify.pc.remote.actions.PcActionIcon
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction

@Composable
fun PcText.resolve(): String = when (this) {
    is PcText.Res -> stringResource(id, *args.toTypedArray())
    is PcText.Literal -> text
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
fun PcActionControl(action: PcResolvedAction, onClick: () -> Unit, modifier: Modifier = Modifier, stacked: Boolean = false) {
    val presentation = action.presentation
    PcControlButton(
        label = presentation.label.resolve(),
        accessibilityLabel = presentation.accessibilityLabel?.resolve(),
        onClick = onClick,
        modifier = modifier,
        icon = presentation.icon?.vector(),
        selected = presentation.selected,
        enabled = action.enabled,
        danger = presentation.danger,
        emphasized = presentation.emphasized,
        keySize = presentation.keySize,
        stacked = stacked
    )
}

@Composable
fun PcControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String? = null,
    icon: ImageVector? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    emphasized: Boolean = false,
    keySize: Boolean = false,
    stacked: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val isSelected = selected == true
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
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .heightIn(min = if (keySize) 64.dp else 58.dp)
            .semantics {
                if (accessibilityLabel != null) contentDescription = accessibilityLabel
                if (selected != null) this.selected = selected
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
                ButtonContent(label, icon, isSelected)
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
private fun ButtonContent(label: String, icon: ImageVector?, selected: Boolean) {
    if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center
    )
    if (selected) {
        Icon(imageVector = Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}
