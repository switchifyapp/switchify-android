package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.screens.pc.pcSurfaceLabel
import com.enaboapps.switchify.theme.Dimens

private val FieldShape = RoundedCornerShape(20.dp)

@Composable
fun PcSurfaceSelector(selected: PcRemoteSurface, onSelect: (PcRemoteSurface) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val fieldText = stringResource(R.string.pc_surface_field, stringResource(pcSurfaceLabel(selected)))
    val openLabel = stringResource(R.string.pc_surface_field_open)
    Surface(
        onClick = { open = true },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                onClick(label = openLabel) {
                    open = true
                    true
                }
            },
        shape = FieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = fieldText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.UnfoldMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    if (open) {
        PcSurfaceDialog(
            selected = selected,
            onSelect = { surface ->
                open = false
                onSelect(surface)
            },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun PcSurfaceDialog(selected: PcRemoteSurface, onSelect: (PcRemoteSurface) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = FieldShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(Dimens.spaceL),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
            ) {
                Text(
                    text = stringResource(R.string.pc_surface_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
                ) {
                    PcRemoteSurface.entries.forEach { surface ->
                        val isSelected = surface == selected
                        PcControlButton(
                            label = stringResource(pcSurfaceLabel(surface)),
                            onClick = { onSelect(surface) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = if (isSelected) Icons.Rounded.Check else null,
                            selected = isSelected
                        )
                    }
                }
                ActionButton(
                    textResId = R.string.button_close,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    type = ActionButtonType.SECONDARY,
                    applyPadding = false,
                    leadingIcon = Icons.Rounded.Close
                )
            }
        }
    }
}
