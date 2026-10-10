package com.enaboapps.switchify.pc.forwarding

import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcCommandTypes
import com.enaboapps.switchify.pc.protocol.PcCommands
import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.protocol.PcResponse
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import com.enaboapps.switchify.pc.protocol.PcSwitchBinding
import com.enaboapps.switchify.pc.protocol.PcSwitchBindingBehavior
import com.enaboapps.switchify.pc.protocol.PcSwitchProfile
import com.enaboapps.switchify.pc.protocol.PcSwitchProfileKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

interface PcForwardingConnection {
    suspend fun request(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.Ack): PcResponse?

    suspend fun send(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.Ack): Boolean
}

enum class PcForwardingPhase {
    Idle,
    Starting,
    Active,
    Failed
}

enum class PcForwardingMessage {
    NoProfiles,
    Unsupported,
    CaptureUnavailable,
    StartFailed,
    SwitchifyUnavailable,
    SwitchifyRemoteForwarding,
    QueueFull,
    MissedEdge,
    ConfigurationChanged,
    HeldToStop,
    PcScanningStopped,
    IdleTimeout,
    ProfileChanged,
    SelectionSaveFailed,
    Revoked,
    LeftScreen
}

data class PcForwardingMapping(
    val keyCode: Int,
    val name: String,
    val switchId: Int,
    val outputLabel: String?,
    val pressed: Boolean,
    val downTimeMs: Long?
)

data class PcForwardingState(
    val phase: PcForwardingPhase = PcForwardingPhase.Idle,
    val profiles: List<PcSwitchProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val mappings: List<PcForwardingMapping> = emptyList(),
    val overflow: List<String> = emptyList(),
    val message: PcForwardingMessage? = null
) {
    val running: Boolean
        get() = phase == PcForwardingPhase.Starting || phase == PcForwardingPhase.Active

    val selectedProfile: PcSwitchProfile?
        get() = profiles.firstOrNull { it.id == selectedProfileId }
}

class PcForwardingController(
    private val connection: PcForwardingConnection,
    private val bridge: PcSwitchBridge,
    private val pointerProfile: PcPointerProfile,
    private val scope: CoroutineScope,
    private val holdToStopMs: () -> Long = { DEFAULT_HOLD_TO_STOP_MS },
    private val sessionIds: () -> String = { UUID.randomUUID().toString() },
    private val onSafetyStop: () -> Unit = {}
) {
    private val _state = MutableStateFlow(PcForwardingState())
    val state: StateFlow<PcForwardingState> = _state.asStateFlow()

    private var generation = 0L
    private var sessionId = ""
    private var sequence = 0L
    private var bridgeSequence = 0L
    private var legacy = false
    private var attempt = 0
    private var disposed = false
    private var stopping: Deferred<Unit>? = null
    private var starting: Deferred<Boolean>? = null
    private var pending = 0
    private var expectedSwitches = emptyList<PcExternalSwitch>()
    private var syncJob: Job? = null
    private var idleJob: Job? = null
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val unsubscribe: () -> Unit

    init {
        scope.launch {
            for (operation in queue) operation()
        }
        unsubscribe = bridge.subscribe { event -> scope.launch { accept(event) } }
    }

    fun selectedProfile(): PcSwitchProfile? = _state.value.selectedProfile

    suspend fun loadProfiles(remembered: String? = null) {
        val capabilities = pointerProfile.capabilities
        if (GENERIC_COMMANDS.all(capabilities::supports)) {
            val response = connection.request(PcCommands.switchProfileList(capabilities.switchScanning))
            if (response is PcResponse.SwitchProfileCatalog) {
                val profiles = response.catalog.profiles
                val selected = profiles.firstOrNull { it.id == remembered } ?: profiles.firstOrNull()
                set {
                    copy(
                        profiles = profiles,
                        selectedProfileId = selected?.id,
                        message = if (selected == null) PcForwardingMessage.NoProfiles else null
                    )
                }
                return
            }
        }
        if (capabilities.supports(PcCommandTypes.GRID_SWITCH_SET)) {
            legacy = true
            set { copy(profiles = listOf(LEGACY_GRID3_PROFILE), selectedProfileId = LEGACY_GRID3_PROFILE.id, message = null) }
            return
        }
        set { copy(phase = PcForwardingPhase.Failed, message = PcForwardingMessage.Unsupported) }
    }

    fun selectProfile(profileId: String) {
        if (_state.value.profiles.any { it.id == profileId }) set { copy(selectedProfileId = profileId) }
    }

    fun report(message: PcForwardingMessage) = set { copy(message = message) }

    suspend fun start(): Boolean {
        stopping?.await()
        starting?.let { inFlight ->
            if (_state.value.running) return false
            inFlight.await()
        }
        if (_state.value.running) return false
        val job = scope.async(start = CoroutineStart.UNDISPATCHED) { startNow() }
        starting = job
        try {
            return job.await()
        } finally {
            if (starting === job) starting = null
        }
    }

    suspend fun stop(message: PcForwardingMessage? = null, safety: Boolean = false) {
        stopping?.let { return it.await() }
        attempt += 1
        if (!_state.value.running) return
        if (safety) onSafetyStop()
        val stoppedGeneration = generation
        clearTimers()
        set {
            copy(
                phase = PcForwardingPhase.Idle,
                mappings = mappings.map { it.copy(pressed = false, downTimeMs = null) },
                message = message
            )
        }
        val job = scope.async(start = CoroutineStart.UNDISPATCHED) {
            bridge.setForwardingActive(stoppedGeneration, false)
            enqueue { stopPc() }.await()
        }
        stopping = job
        try {
            job.await()
        } finally {
            if (stopping === job) stopping = null
        }
    }

    suspend fun cleanup() {
        disposed = true
        stop()
        unsubscribe()
        queue.close()
    }

    private suspend fun startNow(): Boolean {
        if (_state.value.running || disposed) return false
        val current = ++attempt
        val snapshot = bridge.snapshot()
        val selected = selectedProfile()
        if (!snapshot.captureAvailable || snapshot.externalSwitches.isEmpty()) {
            set { copy(phase = PcForwardingPhase.Failed, message = PcForwardingMessage.CaptureUnavailable) }
            return false
        }
        if (selected == null) return false
        set { copy(phase = PcForwardingPhase.Starting, message = null) }
        generation = bridge.nextGeneration()
        sessionId = sessionIds()
        sequence = 0
        bridgeSequence = 0
        val external = snapshot.externalSwitches.sortedBy { it.keyCode }
        expectedSwitches = external.take(MAX_SWITCHES)
        val mappings = expectedSwitches.mapIndexed { index, item ->
            PcForwardingMapping(
                keyCode = item.keyCode,
                name = item.name,
                switchId = index + 1,
                outputLabel = selected.bindings.firstOrNull {
                    it.switchId == index + 1 && it.behavior != PcSwitchBindingBehavior.Unassigned
                }?.label,
                pressed = false,
                downTimeMs = null
            )
        }
        if (!legacy) {
            val ok = selected.version in 1..Int.MAX_VALUE.toLong() && connection.send(
                PcCommands.switchSessionStart(sessionId, selected.id, selected.version.toInt(), mappings.size)
            )
            if (!ok) {
                set { copy(phase = PcForwardingPhase.Failed, message = PcForwardingMessage.StartFailed) }
                return false
            }
            if (current != attempt || disposed) {
                stopPc()
                return false
            }
        }
        if (!bridge.setForwardingActive(generation, true)) {
            val remoteOwnsSwitches = bridge.forwardingOwnedBySwitchifyRemote()
            stopPc()
            if (current == attempt && !disposed) {
                set {
                    copy(
                        phase = PcForwardingPhase.Failed,
                        message = if (remoteOwnsSwitches) PcForwardingMessage.SwitchifyRemoteForwarding
                        else PcForwardingMessage.SwitchifyUnavailable
                    )
                }
            }
            return false
        }
        if (current != attempt || disposed) {
            bridge.setForwardingActive(generation, false)
            stopPc()
            return false
        }
        set {
            copy(
                phase = PcForwardingPhase.Active,
                mappings = mappings,
                overflow = external.drop(MAX_SWITCHES).map { it.name },
                message = null
            )
        }
        syncNow(heldIds(), current)
        if (current != attempt || disposed) return false
        syncJob = scope.launch {
            while (true) {
                delay(SYNC_INTERVAL_MS)
                if (pending >= MAX_PENDING) {
                    fire { stop(PcForwardingMessage.QueueFull, safety = true) }
                    return@launch
                }
                val held = heldIds()
                enqueue { syncNow(held, current) }
            }
        }
        resetIdle()
        return true
    }

    private fun accept(event: PcSwitchBridgeEvent) {
        val current = _state.value
        when (event) {
            is PcSwitchBridgeEvent.Revoked -> {
                if (event.generation == generation && current.running) {
                    fire { stop(PcForwardingMessage.Revoked, safety = true) }
                }
                return
            }
            is PcSwitchBridgeEvent.Snapshot -> {
                if (!current.running) return
                val configured = event.snapshot.externalSwitches.sortedBy { it.keyCode }.take(MAX_SWITCHES)
                val mapped = if (current.phase == PcForwardingPhase.Starting) expectedSwitches
                else current.mappings.map { PcExternalSwitch(it.keyCode, it.name) }
                if (!event.snapshot.captureAvailable || configured.isEmpty() || configured != mapped) {
                    fire { stop(PcForwardingMessage.ConfigurationChanged, safety = true) }
                }
                return
            }
            is PcSwitchBridgeEvent.SwitchEdge -> acceptEdge(event)
        }
    }

    private fun acceptEdge(event: PcSwitchBridgeEvent.SwitchEdge) {
        if (event.generation != generation || _state.value.phase != PcForwardingPhase.Active) return
        if (pending >= MAX_PENDING) {
            fire { stop(PcForwardingMessage.QueueFull, safety = true) }
            return
        }
        if (event.sequence != bridgeSequence + 1) {
            fire { stop(PcForwardingMessage.MissedEdge, safety = true) }
            return
        }
        bridgeSequence = event.sequence
        val mapping = _state.value.mappings.firstOrNull { it.keyCode == event.keyCode } ?: return
        resetIdle()
        val duration = (event.eventTimeMs - event.downTimeMs).coerceAtLeast(0)
        val holdToStop = holdToStopMs()
        val heldLongEnough = !event.down && !event.cancelled && duration >= holdToStop
        val scanning = selectedProfile()?.kind == PcSwitchProfileKind.Scanning
        if (scanning && heldLongEnough) {
            fire { stop(PcForwardingMessage.HeldToStop, safety = true) }
            return
        }
        if (scanning && event.cancelled) {
            updateMapping(event.keyCode, pressed = false, downTimeMs = null)
            val current = attempt
            val held = heldIds()
            enqueue { syncNow(held, current) }
            return
        }
        val replacement = event.down && mapping.pressed && mapping.downTimeMs != event.downTimeMs
        val withdrawn = if (replacement && scanning) heldIds().filter { it != mapping.switchId } else null
        updateMapping(event.keyCode, pressed = event.down, downTimeMs = if (event.down) event.downTimeMs else null)
        val current = attempt
        enqueue {
            if (current != attempt || _state.value.phase != PcForwardingPhase.Active) return@enqueue
            if (withdrawn != null) syncNow(withdrawn, current)
            else if (replacement) edge(mapping.switchId, down = false)
            if (current != attempt || _state.value.phase != PcForwardingPhase.Active) return@enqueue
            edge(mapping.switchId, event.down)
            if (heldLongEnough) fire { stop(PcForwardingMessage.HeldToStop, safety = true) }
        }
    }

    private suspend fun edge(switchId: Int, down: Boolean): Boolean {
        sequence += 1
        val capabilities = pointerProfile.capabilities
        val type = if (legacy) PcCommandTypes.GRID_SWITCH_SET else PcCommandTypes.SWITCH_EDGE
        val command = when {
            !legacy -> PcCommands.switchEdge(switchId, down, sessionId, sequence)
            capabilities.supports(PcCommandTypes.GRID_SWITCH_SYNC) -> PcCommands.gridSwitchSet(switchId, down, sessionId, sequence)
            else -> PcCommands.gridSwitchSet(switchId, down)
        }
        val responseMode = if (!scanningSelected() && capabilities.supportsNoAck(type)) PcResponseMode.None else PcResponseMode.Ack
        val ok = sendSafely(command, responseMode)
        if (!ok && scanningSelected()) fire { stop(PcForwardingMessage.PcScanningStopped, safety = true) }
        return ok
    }

    private suspend fun syncNow(held: List<Int>, current: Int) {
        if (current != attempt || _state.value.phase != PcForwardingPhase.Active) return
        if (legacy && !pointerProfile.capabilities.supports(PcCommandTypes.GRID_SWITCH_SYNC)) return
        sequence += 1
        val command = if (legacy) PcCommands.gridSwitchSync(sessionId, sequence, held)
        else PcCommands.switchSync(sessionId, sequence, held)
        val ok = sendSafely(command)
        if (current == attempt && !ok && scanningSelected()) {
            fire { stop(PcForwardingMessage.PcScanningStopped, safety = true) }
        }
    }

    private suspend fun stopPc() {
        sequence += 1
        if (legacy) {
            if (pointerProfile.capabilities.supports(PcCommandTypes.GRID_SWITCH_SYNC)) {
                sendSafely(PcCommands.gridSwitchSync(sessionId, sequence, emptyList()))
            } else {
                _state.value.mappings.forEach { mapping ->
                    sendSafely(PcCommands.gridSwitchSet(mapping.switchId, down = false))
                }
            }
        } else {
            sendSafely(PcCommands.switchSessionStop(sessionId, sequence))
        }
    }

    private fun resetIdle() {
        idleJob?.cancel()
        idleJob = null
        if (scanningSelected()) return
        idleJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            fire { stop(PcForwardingMessage.IdleTimeout, safety = true) }
        }
    }

    private fun clearTimers() {
        syncJob?.cancel()
        idleJob?.cancel()
        syncJob = null
        idleJob = null
    }

    private fun heldIds(): List<Int> = _state.value.mappings.filter { it.pressed }.map { it.switchId }

    private fun scanningSelected() = selectedProfile()?.kind == PcSwitchProfileKind.Scanning

    private fun updateMapping(keyCode: Int, pressed: Boolean, downTimeMs: Long?) = set {
        copy(mappings = mappings.map { if (it.keyCode == keyCode) it.copy(pressed = pressed, downTimeMs = downTimeMs) else it })
    }

    private fun enqueue(operation: suspend () -> Unit): Deferred<Unit> {
        val done = CompletableDeferred<Unit>()
        pending += 1
        val queued = queue.trySend {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            } finally {
                pending -= 1
                done.complete(Unit)
            }
        }
        if (!queued.isSuccess) {
            pending -= 1
            done.complete(Unit)
        }
        return done
    }

    private fun fire(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { block() }
    }

    private suspend fun sendSafely(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.Ack): Boolean = try {
        connection.send(command, responseMode)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    private fun set(transform: PcForwardingState.() -> PcForwardingState) = _state.update(transform)

    companion object {
        const val DEFAULT_HOLD_TO_STOP_MS = 5_000L
        const val SYNC_INTERVAL_MS = 1_000L
        const val IDLE_TIMEOUT_MS = 60_000L
        const val MAX_SWITCHES = 8
        const val MAX_PENDING = 64
        const val LEGACY_GRID3_PROFILE_ID = "legacy.grid3"

        val GENERIC_COMMANDS = listOf(
            PcCommandTypes.SWITCH_PROFILE_LIST,
            PcCommandTypes.SWITCH_SESSION_START,
            PcCommandTypes.SWITCH_EDGE,
            PcCommandTypes.SWITCH_SYNC,
            PcCommandTypes.SWITCH_SESSION_STOP
        )

        val LEGACY_GRID3_PROFILE = PcSwitchProfile(
            id = LEGACY_GRID3_PROFILE_ID,
            version = 1,
            name = "Grid 3",
            kind = PcSwitchProfileKind.Grid3,
            bindings = (1..MAX_SWITCHES).map { PcSwitchBinding(it, "Switch $it", PcSwitchBindingBehavior.Stateful) }
        )
    }
}
