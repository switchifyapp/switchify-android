package com.enaboapps.switchify.pc.connection

import com.enaboapps.switchify.pc.protocol.PcClock
import com.enaboapps.switchify.pc.transport.PcConnectionStage
import com.enaboapps.switchify.pc.transport.PcConnectionStageObserver
import com.enaboapps.switchify.pc.transport.PcConnectionStageOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

enum class PcDiagnosticLevel(val label: String) {
    Info("info"),
    Warning("warning"),
    Error("error")
}

data class PcDiagnosticEntry(
    val id: Long,
    val timestamp: Long,
    val level: PcDiagnosticLevel,
    val code: String,
    val message: String
)

enum class PcDiagnosticEvent(val code: String, val message: String) {
    ScanStarted("scan_started", "Looking for nearby PCs."),
    ScanFailed("scan_failed", "Bluetooth discovery could not start."),
    Connecting("connecting", "Connecting to a PC."),
    Connected("connected", "Connected to a PC."),
    ConnectionLost("connection_lost", "The connection to the PC was lost."),
    ConnectionHealthFailed("connection_health_failed", "The PC did not respond to a Bluetooth connection check."),
    ProfileRecoveryStarted("profile_recovery_started", "Restoring remote controls."),
    ProfileRecovered("profile_recovered", "Remote controls were restored."),
    ProfileRecoveryExhausted("profile_recovery_exhausted", "Remote controls could not be restored."),
    PairingRequested("pairing_requested", "Pairing approval requested."),
    PairingRejected("pairing_rejected", "Pairing was not approved."),
    AuthenticationFailed("authentication_failed", "Saved access is no longer valid."),
    Disconnected("disconnected", "Disconnected from the PC."),
    CommandFailed("command_failed", "A remote command failed."),
    RemoteNameSyncFailed("remote_name_sync_failed", "The Remote name could not be updated on the PC."),
    UnpairFailed("unpair_failed", "A saved PC could not be removed."),
    CleanupComplete("cleanup_complete", "Remote input state was cleaned up.")
}

class PcDiagnosticLog(private val clock: PcClock = PcClock.System) : PcConnectionStageObserver {
    private val nextId = AtomicLong(1)
    private val _entries = MutableStateFlow<List<PcDiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<PcDiagnosticEntry>> = _entries.asStateFlow()

    fun add(event: PcDiagnosticEvent, level: PcDiagnosticLevel = PcDiagnosticLevel.Info) {
        append(event.code, event.message, level)
    }

    fun addConnectionStage(stage: PcConnectionStage, outcome: PcConnectionStageOutcome) {
        val level = if (outcome == PcConnectionStageOutcome.Failed || outcome == PcConnectionStageOutcome.TimedOut) {
            PcDiagnosticLevel.Warning
        } else {
            PcDiagnosticLevel.Info
        }
        append("${stage.code}_${outcome.code}", "${stageDescription(stage)}: ${outcome.code}.", level)
    }

    override fun onConnectionStage(stage: PcConnectionStage, outcome: PcConnectionStageOutcome) =
        addConnectionStage(stage, outcome)

    fun clear() {
        _entries.value = emptyList()
    }

    fun export(): String = _entries.value.joinToString("\n") { entry ->
        "${TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(entry.timestamp))} [${entry.level.label}] ${entry.code}: ${entry.message}"
    }

    private fun append(code: String, message: String, level: PcDiagnosticLevel) {
        val entry = PcDiagnosticEntry(nextId.getAndIncrement(), clock.nowMillis(), level, code, message)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    companion object {
        const val MAX_ENTRIES = 200

        private val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        fun stageDescription(stage: PcConnectionStage): String = when (stage) {
            PcConnectionStage.ProbeConnect -> "Connect for discovery status"
            PcConnectionStage.ProbeServices -> "Discover status services"
            PcConnectionStage.StatusRead -> "Read discovery status"
            PcConnectionStage.StatusParse -> "Parse discovery status"
            PcConnectionStage.SelectedMatch -> "Match discovery status to the selected PC"
            PcConnectionStage.Resolution -> "Resolve and prepare the selected PC connection"
            PcConnectionStage.Connect -> "Connect to the selected PC"
            PcConnectionStage.Priority -> "Request Android connection priority (optional)"
            PcConnectionStage.Mtu -> "Negotiate Bluetooth MTU"
            PcConnectionStage.Services -> "Discover connection services"
            PcConnectionStage.Notifications -> "Register notification listener"
            PcConnectionStage.NotificationReady -> "Verify Android notification descriptor"
        }
    }
}
