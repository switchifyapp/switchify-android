package com.enaboapps.switchify.screens.pc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.pc.connection.PcConnectionGraph
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcProfileStatus
import com.enaboapps.switchify.pc.forwarding.InAppSwitchBridge
import com.enaboapps.switchify.pc.forwarding.PcForwardingMapping
import com.enaboapps.switchify.pc.forwarding.PcForwardingMessage
import com.enaboapps.switchify.pc.forwarding.PcForwardingPhase
import com.enaboapps.switchify.pc.forwarding.PcForwardingPreferences
import com.enaboapps.switchify.pc.forwarding.PcForwardingState
import com.enaboapps.switchify.pc.forwarding.SharedPreferencesPcForwardingPreferenceStore
import com.enaboapps.switchify.pc.protocol.PcSwitchProfile
import com.enaboapps.switchify.pc.protocol.PcSwitchProfileKind
import com.enaboapps.switchify.theme.Dimens
import com.enaboapps.switchify.utils.findActivity

@Composable
fun PcForwardingSurface(onOpenPcs: () -> Unit) {
    val context = LocalContext.current
    val graph = remember { PcConnectionGraph.getInstance(context) }
    val viewModel: PcForwardingViewModel = viewModel {
        PcForwardingViewModel(
            manager = graph.manager,
            bridge = InAppSwitchBridge,
            preferences = SharedPreferencesPcForwardingPreferenceStore(context)
        )
    }
    val connection by viewModel.connection.collectAsState()
    val forwarding by viewModel.forwarding.collectAsState()
    val holdToStopMs by viewModel.holdToStopMs.collectAsState()
    val holdToStopLabels = PcForwardingPreferences.HOLD_TO_STOP_OPTIONS_MS.associate { milliseconds ->
        val seconds = (milliseconds / 1000).toInt()
        milliseconds.toInt() to pluralStringResource(R.plurals.pc_forwarding_seconds, seconds, seconds)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel, context) {
        val activity = context.findActivity()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.attach()
                Lifecycle.Event.ON_STOP -> viewModel.detach(changingConfigurations = activity?.isChangingConfigurations == true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.detach(changingConfigurations = activity?.isChangingConfigurations == true)
        }
    }
    LaunchedEffect(viewModel) { viewModel.refreshHoldToStop() }

    val view = LocalView.current
    val forwardingActive = forwarding.phase == PcForwardingPhase.Active
    DisposableEffect(view, forwardingActive) {
        view.keepScreenOn = forwardingActive
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
    ) {
        Text(
            text = stringResource(R.string.pc_forwarding_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (val current = connection) {
            is PcConnectionState.Connected -> if (current.profile == null) {
                InfoPanel(
                    title = stringResource(
                        if (current.profileStatus == PcProfileStatus.Recovering) R.string.pc_forwarding_profile_recovering_title
                        else R.string.pc_forwarding_profile_unavailable_title
                    ),
                    body = stringResource(R.string.pc_forwarding_profile_unavailable_body)
                )
            } else {
                ForwardingBody(
                    pcName = current.desktop.displayName,
                    state = forwarding,
                    holdToStopMs = holdToStopMs,
                    onSelect = viewModel::selectProfile,
                    onToggle = viewModel::toggle
                )
            }
            else -> InfoPanel(
                title = stringResource(R.string.pc_forwarding_not_connected_title),
                body = stringResource(R.string.pc_forwarding_not_connected_body)
            ) {
                ActionButton(
                    textResId = R.string.pc_forwarding_open_pcs,
                    type = ActionButtonType.SECONDARY,
                    leadingIcon = Icons.Rounded.Computer,
                    onClick = onOpenPcs,
                    applyPadding = false
                )
            }
        }

        Text(
            text = stringResource(R.string.pc_forwarding_settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Panel(modifier = Modifier.fillMaxWidth()) {
            PreferenceValueSelector(
                value = holdToStopMs.toInt(),
                titleResId = R.string.pc_forwarding_hold_to_stop_title,
                summaryResId = R.string.pc_forwarding_hold_to_stop_summary,
                values = PcForwardingPreferences.HOLD_TO_STOP_OPTIONS_MS.map { it.toInt() }.toIntArray(),
                buttonLabelFormatter = { holdToStopLabels.getValue(it) },
                displayFormatter = { holdToStopLabels.getValue(it) },
                onValueChanged = { viewModel.setHoldToStopMs(it.toLong()) }
            )
        }
    }
}

@Composable
private fun ForwardingBody(
    pcName: String,
    state: PcForwardingState,
    holdToStopMs: Long,
    onSelect: (String) -> Unit,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val seconds = (holdToStopMs / 1000).toInt()
    Panel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (state.phase == PcForwardingPhase.Active) scheme.primaryContainer else scheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.spaceM)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            Text(
                text = stringResource(phaseTitle(state.phase)),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = when (state.phase) {
                    PcForwardingPhase.Active -> pluralStringResource(R.plurals.pc_forwarding_active_body, seconds, pcName, seconds)
                    else -> stringResource(R.string.pc_forwarding_idle_body, pcName)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            state.message?.let { message ->
                Text(
                    text = stringResource(messageText(message)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.informational) MaterialTheme.colorScheme.onSurface else scheme.error
                )
            }
        }
    }

    if (state.selectedProfile?.kind == PcSwitchProfileKind.Scanning) {
        Text(
            text = stringResource(R.string.pc_forwarding_scanning_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
    }

    if (state.profiles.isNotEmpty()) {
        Text(
            text = stringResource(R.string.pc_forwarding_profiles_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Panel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.selectableGroup()) {
                state.profiles.forEach { profile ->
                    ProfileRow(
                        profile = profile,
                        selected = profile.id == state.selectedProfileId,
                        enabled = !state.running,
                        onSelect = { onSelect(profile.id) }
                    )
                }
            }
        }
    }

    ActionButton(
        textResId = if (state.phase == PcForwardingPhase.Active) R.string.pc_forwarding_stop else R.string.pc_forwarding_start,
        type = if (state.phase == PcForwardingPhase.Active) ActionButtonType.DESTRUCTIVE else ActionButtonType.PRIMARY,
        enabled = state.phase != PcForwardingPhase.Starting && state.profiles.isNotEmpty(),
        leadingIcon = if (state.phase == PcForwardingPhase.Active) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
        onClick = onToggle,
        applyPadding = false,
        modifier = Modifier.fillMaxWidth()
    )

    if (state.phase == PcForwardingPhase.Active && state.overflow.isNotEmpty()) {
        Text(
            text = pluralStringResource(R.plurals.pc_forwarding_overflow, state.overflow.size, state.overflow.size),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.error
        )
    }

    if (state.phase == PcForwardingPhase.Active) {
        Text(
            text = stringResource(R.string.pc_forwarding_switches_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
            state.mappings.forEach { mapping -> MappingRow(mapping) }
        }
    }
}

@Composable
private fun ProfileRow(profile: PcSwitchProfile, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    when (profile.kind) {
                        PcSwitchProfileKind.Grid3 -> R.string.pc_forwarding_kind_grid3
                        PcSwitchProfileKind.Mapped -> R.string.pc_forwarding_kind_mapped
                        PcSwitchProfileKind.Scanning -> R.string.pc_forwarding_kind_scanning
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MappingRow(mapping: PcForwardingMapping) {
    val scheme = MaterialTheme.colorScheme
    val output = when {
        mapping.outputLabel == null -> stringResource(R.string.pc_forwarding_unassigned)
        mapping.outputLabel.isBlank() -> stringResource(R.string.pc_forwarding_switch_number, mapping.switchId)
        else -> mapping.outputLabel
    }
    val status = stringResource(if (mapping.pressed) R.string.pc_forwarding_pressed else R.string.pc_forwarding_released)
    val description = stringResource(R.string.pc_forwarding_mapping_description, mapping.name, output, status)
    Surface(
        color = if (mapping.pressed) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (mapping.pressed) scheme.onPrimaryContainer else scheme.onSurface,
        border = BorderStroke(if (mapping.pressed) 2.dp else 1.dp, if (mapping.pressed) scheme.primary else scheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Row(
            modifier = Modifier.padding(Dimens.spaceM),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = mapping.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                text = if (mapping.pressed) stringResource(R.string.pc_forwarding_output_pressed, output) else output,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InfoPanel(title: String, body: String, action: @Composable () -> Unit = {}) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            action()
        }
    }
}

private fun phaseTitle(phase: PcForwardingPhase): Int = when (phase) {
    PcForwardingPhase.Idle -> R.string.pc_forwarding_status_idle
    PcForwardingPhase.Starting -> R.string.pc_forwarding_status_starting
    PcForwardingPhase.Active -> R.string.pc_forwarding_status_active
    PcForwardingPhase.Failed -> R.string.pc_forwarding_status_failed
}

private fun messageText(message: PcForwardingMessage): Int = when (message) {
    PcForwardingMessage.NoProfiles -> R.string.pc_forwarding_message_no_profiles
    PcForwardingMessage.Unsupported -> R.string.pc_forwarding_message_unsupported
    PcForwardingMessage.CaptureUnavailable -> R.string.pc_forwarding_message_capture_unavailable
    PcForwardingMessage.StartFailed -> R.string.pc_forwarding_message_start_failed
    PcForwardingMessage.SwitchifyUnavailable -> R.string.pc_forwarding_message_switchify_unavailable
    PcForwardingMessage.SwitchifyRemoteForwarding -> R.string.pc_forwarding_message_remote_forwarding
    PcForwardingMessage.QueueFull -> R.string.pc_forwarding_message_queue_full
    PcForwardingMessage.MissedEdge -> R.string.pc_forwarding_message_missed_edge
    PcForwardingMessage.ConfigurationChanged -> R.string.pc_forwarding_message_configuration_changed
    PcForwardingMessage.HeldToStop -> R.string.pc_forwarding_message_held_to_stop
    PcForwardingMessage.PcScanningStopped -> R.string.pc_forwarding_message_pc_scanning_stopped
    PcForwardingMessage.IdleTimeout -> R.string.pc_forwarding_message_idle_timeout
    PcForwardingMessage.ProfileChanged -> R.string.pc_forwarding_message_profile_changed
    PcForwardingMessage.SelectionSaveFailed -> R.string.pc_forwarding_message_selection_save_failed
    PcForwardingMessage.Revoked -> R.string.pc_forwarding_message_revoked
    PcForwardingMessage.LeftScreen -> R.string.pc_forwarding_message_left_screen
}
