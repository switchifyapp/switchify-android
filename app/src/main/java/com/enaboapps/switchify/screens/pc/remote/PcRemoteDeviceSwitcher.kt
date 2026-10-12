package com.enaboapps.switchify.screens.pc.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.remote.PcRemotePresentation
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PcRemoteDeviceSwitcher(
    connection: PcConnectionState,
    loadSaved: suspend () -> List<PcSavedPc>,
    onSelect: (PcSavedPc) -> Unit,
    onManagePcs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val presentation = PcRemotePresentation.device(connection)
    val name = presentation.name.resolve()
    val status = stringResource(presentation.statusRes)
    val label = stringResource(R.string.pc_switcher_label)
    val description = stringResource(R.string.pc_switcher_description, label, status, name)
    Surface(
        onClick = { open = true },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = label) {
                    open = true
                    true
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceXs),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = Icons.Rounded.KeyboardArrowUp, contentDescription = null)
        }
    }
    if (open) {
        val currentDesktopId = connection.activeDesktop?.desktopId
        SwitcherDialog(
            currentDesktopId = currentDesktopId,
            loadSaved = loadSaved,
            onSelect = { pc ->
                open = false
                onSelect(pc)
            },
            onManagePcs = {
                open = false
                onManagePcs()
            },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun SwitcherDialog(
    currentDesktopId: String?,
    loadSaved: suspend () -> List<PcSavedPc>,
    onSelect: (PcSavedPc) -> Unit,
    onManagePcs: () -> Unit,
    onDismiss: () -> Unit
) {
    var saved by remember { mutableStateOf<List<PcSavedPc>?>(null) }
    LaunchedEffect(Unit) { saved = loadSaved() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pc_switcher_label)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
            ) {
                when (val pcs = saved) {
                    null -> Text(
                        text = stringResource(R.string.pc_switcher_loading),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    else -> if (pcs.isEmpty()) {
                        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                            Text(text = stringResource(R.string.pc_switcher_empty_title), style = MaterialTheme.typography.titleSmall)
                            Text(text = stringResource(R.string.pc_switcher_empty_body), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        pcs.forEach { pc ->
                            PcControlButton(
                                label = pc.displayName,
                                onClick = { onSelect(pc) },
                                selected = pc.desktopId == currentDesktopId,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                ActionButton(
                    textResId = R.string.pc_switcher_manage,
                    onClick = onManagePcs,
                    type = ActionButtonType.SECONDARY,
                    leadingIcon = Icons.Rounded.SettingsRemote,
                    applyPadding = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pc_switcher_close)) }
        }
    )
}
