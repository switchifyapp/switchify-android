package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.connection.PcDiagnosticDetail
import com.enaboapps.switchify.pc.connection.PcDiagnosticEntry
import com.enaboapps.switchify.pc.connection.PcDiagnosticEvent
import com.enaboapps.switchify.pc.transport.PcConnectionStage
import com.enaboapps.switchify.pc.transport.PcConnectionStageOutcome

@Composable
fun PcDiagnosticEntry.displayMessage(): String = when (val value = detail) {
    is PcDiagnosticDetail.Event -> stringResource(value.event.messageRes())
    is PcDiagnosticDetail.Stage -> stringResource(
        R.string.pc_diagnostics_stage_format,
        stringResource(value.stage.descriptionRes()),
        stringResource(value.outcome.labelRes())
    )
}

@StringRes
fun PcDiagnosticEvent.messageRes(): Int = when (this) {
    PcDiagnosticEvent.ScanStarted -> R.string.pc_diagnostics_event_scan_started
    PcDiagnosticEvent.ScanFailed -> R.string.pc_diagnostics_event_scan_failed
    PcDiagnosticEvent.Connecting -> R.string.pc_diagnostics_event_connecting
    PcDiagnosticEvent.Connected -> R.string.pc_diagnostics_event_connected
    PcDiagnosticEvent.ConnectionLost -> R.string.pc_diagnostics_event_connection_lost
    PcDiagnosticEvent.ConnectionHealthFailed -> R.string.pc_diagnostics_event_connection_health_failed
    PcDiagnosticEvent.ProfileRecoveryStarted -> R.string.pc_diagnostics_event_profile_recovery_started
    PcDiagnosticEvent.ProfileRecovered -> R.string.pc_diagnostics_event_profile_recovered
    PcDiagnosticEvent.ProfileRecoveryExhausted -> R.string.pc_diagnostics_event_profile_recovery_exhausted
    PcDiagnosticEvent.PairingRequested -> R.string.pc_diagnostics_event_pairing_requested
    PcDiagnosticEvent.PairingIntentPublished -> R.string.pc_diagnostics_event_pairing_intent_published
    PcDiagnosticEvent.PairingIntentNotPublished -> R.string.pc_diagnostics_event_pairing_intent_not_published
    PcDiagnosticEvent.PairingRejected -> R.string.pc_diagnostics_event_pairing_rejected
    PcDiagnosticEvent.AuthenticationFailed -> R.string.pc_diagnostics_event_authentication_failed
    PcDiagnosticEvent.Disconnected -> R.string.pc_diagnostics_event_disconnected
    PcDiagnosticEvent.CommandFailed -> R.string.pc_diagnostics_event_command_failed
    PcDiagnosticEvent.RemoteNameSyncFailed -> R.string.pc_diagnostics_event_remote_name_sync_failed
    PcDiagnosticEvent.UnpairFailed -> R.string.pc_diagnostics_event_unpair_failed
    PcDiagnosticEvent.CleanupComplete -> R.string.pc_diagnostics_event_cleanup_complete
}

@StringRes
fun PcConnectionStage.descriptionRes(): Int = when (this) {
    PcConnectionStage.ProbeConnect -> R.string.pc_diagnostics_stage_probe_connect
    PcConnectionStage.ProbeServices -> R.string.pc_diagnostics_stage_probe_services
    PcConnectionStage.StatusRead -> R.string.pc_diagnostics_stage_status_read
    PcConnectionStage.StatusParse -> R.string.pc_diagnostics_stage_status_parse
    PcConnectionStage.SelectedMatch -> R.string.pc_diagnostics_stage_selected_match
    PcConnectionStage.Resolution -> R.string.pc_diagnostics_stage_resolution
    PcConnectionStage.Connect -> R.string.pc_diagnostics_stage_connect
    PcConnectionStage.Priority -> R.string.pc_diagnostics_stage_priority
    PcConnectionStage.Mtu -> R.string.pc_diagnostics_stage_mtu
    PcConnectionStage.Services -> R.string.pc_diagnostics_stage_services
    PcConnectionStage.Notifications -> R.string.pc_diagnostics_stage_notifications
    PcConnectionStage.NotificationReady -> R.string.pc_diagnostics_stage_notification_ready
}

@StringRes
fun PcConnectionStageOutcome.labelRes(): Int = when (this) {
    PcConnectionStageOutcome.Started -> R.string.pc_diagnostics_outcome_started
    PcConnectionStageOutcome.Succeeded -> R.string.pc_diagnostics_outcome_succeeded
    PcConnectionStageOutcome.Failed -> R.string.pc_diagnostics_outcome_failed
    PcConnectionStageOutcome.TimedOut -> R.string.pc_diagnostics_outcome_timed_out
    PcConnectionStageOutcome.NotMatched -> R.string.pc_diagnostics_outcome_not_matched
}
