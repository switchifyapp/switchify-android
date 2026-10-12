package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSurface
import com.enaboapps.switchify.pc.remote.layouts.PcSectionDefinition
import com.enaboapps.switchify.theme.Dimens

@Stable
data class PcLayoutEditing(
    val enabled: Boolean,
    val blocked: String?,
    val open: (PcLayoutSurface, String, List<PcResolvedAction>, Float, Float) -> Unit,
    val refreshControls: (PcLayoutSurface, String, List<PcResolvedAction>) -> Unit
)

val LocalPcLayoutEditing = compositionLocalOf<PcLayoutEditing?> { null }

@Composable
fun PcLayoutEditToggle(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconToggleButton(
        checked = enabled,
        onCheckedChange = { onToggle() },
        modifier = modifier.size(58.dp)
    ) {
        Icon(
            imageVector = if (enabled) Icons.Rounded.Check else Icons.Rounded.Edit,
            contentDescription = stringResource(if (enabled) R.string.pc_layout_done_editing else R.string.pc_layout_edit_layout)
        )
    }
}

@Composable
fun PcSectionEditing(
    surface: PcLayoutSurface,
    section: String,
    definition: PcSectionDefinition,
    controls: List<PcResolvedAction>
) {
    val editing = LocalPcLayoutEditing.current ?: return
    SideEffect { editing.refreshControls(surface, section, controls) }
    if (!editing.enabled) return
    val fontScale = LocalDensity.current.fontScale
    val title = stringResource(definition.titleRes)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val width = maxWidth.value
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
            PcControlButton(
                label = stringResource(R.string.pc_layout_edit_section),
                accessibilityLabel = stringResource(R.string.pc_layout_edit_section_description, title),
                icon = Icons.Rounded.Edit,
                enabled = editing.blocked == null,
                onClick = { editing.open(surface, section, controls, width, fontScale) }
            )
            editing.blocked?.let { blocked ->
                Text(
                    text = blocked,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
