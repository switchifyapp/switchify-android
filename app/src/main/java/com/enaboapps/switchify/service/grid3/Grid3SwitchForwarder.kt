package com.enaboapps.switchify.service.grid3

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcProtocol
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class Grid3SwitchMapping(
    val keyCode: String,
    val name: String,
    val switchId: Int,
    val pressed: Boolean = false
)

enum class Grid3ConnectionStatus {
    Connected,
    Reconnecting
}

data class Grid3ForwardingState(
    val active: Boolean = false,
    val connectionStatus: Grid3ConnectionStatus = Grid3ConnectionStatus.Connected,
    val pcName: String? = null,
    val mappings: List<Grid3SwitchMapping> = emptyList(),
    val overflowSwitches: List<String> = emptyList(),
    val holdToStopDurationMs: Long = Grid3SwitchForwarder.DEFAULT_HOLD_TO_STOP_MS
)

sealed class Grid3StartResult {
    data object Started : Grid3StartResult()
    data object NoExternalSwitches : Grid3StartResult()
    data object UnsupportedPc : Grid3StartResult()
}

internal interface Grid3ForwardingHost {
    val connectionState: StateFlow<PcServiceConnectionState>
    fun currentPointerProfile(): PcPointerMovementProfile?
    fun currentPcName(): String?
    fun configuredSwitches(): List<SwitchEvent>
    fun holdToStopDurationMs(): Long
    fun suspendScanning()
    fun restoreScanning()
    fun maintainConnection()
    fun releaseConnection()
    suspend fun send(command: PcControlCommand): PcCommandResult
    suspend fun sendRealtime(command: PcControlCommand): PcCommandResult = send(command)
}

internal interface Grid3SwitchInputHandler {
    fun onSwitchPressed(keyCode: Int, downTimeMs: Long, eventTimeMs: Long): Boolean
    fun onSwitchReleased(
        keyCode: Int,
        downTimeMs: Long,
        eventTimeMs: Long,
        cancelled: Boolean
    ): Boolean
}

private class AndroidGrid3ForwardingHost(
    private val controller: PcServiceConnectionController,
    private val scanningManager: ScanningManager,
    private val switchEventProvider: SwitchEventProvider,
    private val preferenceManager: PreferenceManager
) : Grid3ForwardingHost {
    override val connectionState: StateFlow<PcServiceConnectionState> = controller.state
    override fun currentPointerProfile() = controller.currentPointerProfile()
    override fun currentPcName() = controller.currentControlDeviceName()
    override fun configuredSwitches() = switchEventProvider.externalSwitches()
    override fun holdToStopDurationMs() = preferenceManager.getLongValue(
        PreferenceManager.PREFERENCE_KEY_GRID3_HOLD_TO_STOP_DURATION,
        Grid3SwitchForwarder.DEFAULT_HOLD_TO_STOP_MS
    )
    override fun suspendScanning() {
        scanningManager.stopMoveRepeat()
        scanningManager.pauseScanning()
        scanningManager.reset()
    }
    override fun restoreScanning() {
        scanningManager.reset()
        scanningManager.resumeScanning()
    }
    override fun maintainConnection() = controller.onPcUiResumed()
    override fun releaseConnection() = controller.onPcUiPaused()
    override suspend fun send(command: PcControlCommand) = controller.sendControlCommand(command)
    override suspend fun sendRealtime(command: PcControlCommand) =
        controller.sendRealtimeControlCommand(command)
}

class Grid3SwitchForwarder internal constructor(
    private val host: Grid3ForwardingHost,
    scope: CoroutineScope
) : Grid3SwitchInputHandler {
    constructor(
        controller: PcServiceConnectionController,
        scanningManager: ScanningManager,
        switchEventProvider: SwitchEventProvider,
        preferenceManager: PreferenceManager,
        scope: CoroutineScope
    ) : this(
        AndroidGrid3ForwardingHost(
            controller,
            scanningManager,
            switchEventProvider,
            preferenceManager
        ),
        scope
    )

    private sealed class EdgeAction {
        data class SetState(
            val generation: Long,
            val keyCode: Int,
            val switchId: Int,
            val down: Boolean,
            val delivery: Grid3Delivery?
        ) : EdgeAction()

        data class RestartPress(
            val generation: Long,
            val keyCode: Int,
            val switchId: Int,
            val releaseDelivery: Grid3Delivery?,
            val pressDelivery: Grid3Delivery?
        ) : EdgeAction()

        data class Stop(
            val generation: Long,
            val completion: CompletableDeferred<Unit>? = null
        ) : EdgeAction()
    }

    private data class SnapshotAction(
        val generation: Long,
        val sessionId: String,
        val sequence: Long,
        val pressedSwitchIds: Set<Int>
    )

    private data class Grid3Delivery(val sessionId: String, val sequence: Long)
    private data class ActivePress(val downTimeMs: Long)

    private val forwardingJob = SupervisorJob()
    private val forwardingScope = CoroutineScope(
        scope.coroutineContext.minusKey(Job) + forwardingJob
    )
    private val stateLock = Any()
    private val edgeActions = Channel<EdgeAction>(Channel.BUFFERED)
    private val snapshotActions = Channel<SnapshotAction>(Channel.CONFLATED)
    private val _state = MutableStateFlow(Grid3ForwardingState())
    val state: StateFlow<Grid3ForwardingState> = _state
    private var generation = 0L
    private var mappingByKeyCode = emptyMap<Int, Grid3SwitchMapping>()
    private val activePresses = mutableMapOf<Int, ActivePress>()
    private val legacyHeldSwitchIds = mutableSetOf<Int>()
    private var gridSessionId: String? = null
    private var nextGridSequence = 0L
    private var snapshotJob: Job? = null
    private var stoppingGeneration: Long? = null
    private var destroying = false

    init {
        forwardingScope.launch {
            for (action in edgeActions) {
                when (action) {
                    is EdgeAction.SetState -> processSetState(action)
                    is EdgeAction.RestartPress -> processRestartPress(action)
                    is EdgeAction.Stop -> {
                        try {
                            processStop(action.generation)
                        } finally {
                            action.completion?.complete(Unit)
                        }
                    }
                }
            }
        }
        forwardingScope.launch {
            for (action in snapshotActions) {
                processSnapshot(action)
            }
        }
        forwardingScope.launch {
            host.connectionState.collect { connectionState ->
                handleConnectionState(connectionState)
            }
        }
    }

    fun start(): Grid3StartResult {
        synchronized(stateLock) {
            if (_state.value.active || stoppingGeneration != null) return Grid3StartResult.Started
        }
        val profile = host.currentPointerProfile()
        if (profile?.capabilities?.supportedCommands?.contains(PcProtocol.GRID_SWITCH_SET_COMMAND) != true) {
            return Grid3StartResult.UnsupportedPc
        }
        val externalSwitches = host.configuredSwitches()
            .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
        if (externalSwitches.isEmpty()) {
            return Grid3StartResult.NoExternalSwitches
        }

        val sorted = sortConfiguredSwitches(externalSwitches)
        val forwarded = sorted.take(MAX_FORWARDED_SWITCHES)
        val holdToStopDurationMs = host.holdToStopDurationMs()
        val useSequencedDelivery =
            profile.capabilities.supportedCommands.contains(PcProtocol.GRID_SWITCH_SYNC_COMMAND) &&
                profile.capabilities.noAckCommands.contains(PcProtocol.GRID_SWITCH_SET_COMMAND)
        val mappings = forwarded.mapIndexed { index, switchEvent ->
            Grid3SwitchMapping(
                keyCode = switchEvent.code,
                name = switchEvent.name,
                switchId = index + 1
            )
        }
        synchronized(stateLock) {
            generation++
            mappingByKeyCode = mappings.mapNotNull { mapping ->
                mapping.keyCode.toIntOrNull()?.let { it to mapping }
            }.toMap()
            activePresses.clear()
            legacyHeldSwitchIds.clear()
            gridSessionId = if (useSequencedDelivery) UUID.randomUUID().toString() else null
            nextGridSequence = 0L
            stoppingGeneration = null
            _state.value = Grid3ForwardingState(
                active = true,
                pcName = host.currentPcName(),
                mappings = mappings,
                overflowSwitches = sorted.drop(MAX_FORWARDED_SWITCHES).map { it.name },
                holdToStopDurationMs = holdToStopDurationMs
            )
        }
        host.suspendScanning()
        host.maintainConnection()
        if (useSequencedDelivery) {
            enqueueSyncSnapshot()
            startSnapshotLoop()
        }
        return Grid3StartResult.Started
    }

    override fun onSwitchPressed(keyCode: Int, downTimeMs: Long, eventTimeMs: Long): Boolean {
        val action = synchronized(stateLock) {
            if (!_state.value.active) {
                return false
            }
            val mapping = mappingByKeyCode[keyCode]
            if (mapping == null) {
                return true
            }
            val activePress = activePresses[keyCode]
            if (activePress?.downTimeMs == downTimeMs) {
                return true
            }
            activePresses[keyCode] = ActivePress(downTimeMs)
            updatePressedState()
            if (activePress == null) {
                EdgeAction.SetState(
                    generation,
                    keyCode,
                    mapping.switchId,
                    down = true,
                    delivery = nextDeliveryLocked()
                )
            } else {
                EdgeAction.RestartPress(
                    generation,
                    keyCode,
                    mapping.switchId,
                    releaseDelivery = nextDeliveryLocked(),
                    pressDelivery = nextDeliveryLocked()
                )
            }
        }
        enqueueEdge(action)
        return true
    }

    override fun onSwitchReleased(
        keyCode: Int,
        downTimeMs: Long,
        eventTimeMs: Long,
        cancelled: Boolean
    ): Boolean {
        val queued = synchronized(stateLock) {
            if (!_state.value.active) {
                return false
            }
            val mapping = mappingByKeyCode[keyCode]
            if (mapping == null) {
                return true
            }
            val activePress = activePresses[keyCode]
            if (activePress == null) {
                return true
            }
            if (activePress.downTimeMs != downTimeMs) {
                return true
            }
            activePresses.remove(keyCode)
            updatePressedState()
            val durationMs = (eventTimeMs - downTimeMs).coerceAtLeast(0L)
            val shouldStop = !cancelled && durationMs >= _state.value.holdToStopDurationMs
            Triple(
                EdgeAction.SetState(
                    generation,
                    keyCode,
                    mapping.switchId,
                    down = false,
                    delivery = nextDeliveryLocked()
                ),
                shouldStop,
                generation
            )
        }
        enqueueEdge(queued.first)
        if (queued.second) {
            enqueueEdge(EdgeAction.Stop(queued.third))
        }
        return true
    }

    suspend fun stop() {
        val currentGeneration = synchronized(stateLock) { generation }
        val completion = CompletableDeferred<Unit>()
        edgeActions.send(EdgeAction.Stop(currentGeneration, completion))
        completion.await()
    }

    fun requestStop() {
        forwardingScope.launch(start = CoroutineStart.UNDISPATCHED) {
            stop()
        }
    }

    suspend fun destroy() {
        prepareForDestroy()
        try {
            stop()
        } finally {
            edgeActions.close()
            snapshotActions.close()
            forwardingJob.cancel()
        }
    }

    fun prepareForDestroy() {
        synchronized(stateLock) {
            destroying = true
        }
    }

    private suspend fun processSetState(action: EdgeAction.SetState) {
        processSetStateOrdered(action)
    }

    private suspend fun processRestartPress(action: EdgeAction.RestartPress) {
        processSetStateOrdered(
            EdgeAction.SetState(
                action.generation,
                action.keyCode,
                action.switchId,
                down = false,
                delivery = action.releaseDelivery
            )
        )
        processSetStateOrdered(
            EdgeAction.SetState(
                action.generation,
                action.keyCode,
                action.switchId,
                down = true,
                delivery = action.pressDelivery
            )
        )
    }

    private suspend fun processSetStateOrdered(action: EdgeAction.SetState) {
        val active = synchronized(stateLock) {
            _state.value.active && generation == action.generation
        }
        if (!active) return
        val command = PcControlCommand.GridSwitchSet(
            switchId = action.switchId,
            down = action.down,
            sessionId = action.delivery?.sessionId,
            sequence = action.delivery?.sequence
        )
        val result = try {
            if (action.delivery == null) {
                host.send(command)
            } else {
                host.sendRealtime(command)
            }
        } catch (error: Exception) {
            PcCommandResult.Failed(error.message ?: "Could not send switch state.")
        }
        if (result == PcCommandResult.Ack && action.delivery == null) {
            synchronized(stateLock) {
                if (generation != action.generation) return@synchronized
                if (action.down) {
                    legacyHeldSwitchIds += action.switchId
                } else {
                    legacyHeldSwitchIds -= action.switchId
                }
            }
        }
    }

    private suspend fun processSnapshot(action: SnapshotAction) {
        val active = synchronized(stateLock) {
            _state.value.active && generation == action.generation
        }
        if (!active) return
        try {
            host.send(
                PcControlCommand.GridSwitchSync(
                    sessionId = action.sessionId,
                    sequence = action.sequence,
                    pressedSwitchIds = action.pressedSwitchIds
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun nextDeliveryLocked(): Grid3Delivery? {
        val sessionId = gridSessionId ?: return null
        nextGridSequence++
        return Grid3Delivery(sessionId, nextGridSequence)
    }

    private fun createSnapshotActionLocked(): SnapshotAction? {
        val sessionId = gridSessionId ?: return null
        if (!_state.value.active) return null
        nextGridSequence++
        val pressedSwitchIds = activePresses.keys.mapNotNull { keyCode ->
            mappingByKeyCode[keyCode]?.switchId
        }.toSet()
        return SnapshotAction(
            generation = generation,
            sessionId = sessionId,
            sequence = nextGridSequence,
            pressedSwitchIds = pressedSwitchIds
        )
    }

    private fun enqueueSyncSnapshot() {
        val action = synchronized(stateLock) { createSnapshotActionLocked() } ?: return
        if (snapshotActions.trySend(action).isFailure) {
            requestStop()
        }
    }

    private fun startSnapshotLoop() {
        snapshotJob?.cancel()
        snapshotJob = forwardingScope.launch {
            while (true) {
                delay(SNAPSHOT_INTERVAL_MS)
                val active = synchronized(stateLock) { _state.value.active }
                if (!active) return@launch
                enqueueSyncSnapshot()
            }
        }
    }

    private suspend fun processStop(expectedGeneration: Long) {
        var finalSync: PcControlCommand.GridSwitchSync? = null
        var legacyHeld = emptyList<Int>()
        val shouldStop = synchronized(stateLock) {
            if (
                generation != expectedGeneration ||
                (!_state.value.active && stoppingGeneration != expectedGeneration)
            ) {
                return
            }
            stoppingGeneration = expectedGeneration
            _state.value = _state.value.copy(active = false)
            activePresses.clear()
            gridSessionId?.let { sessionId ->
                nextGridSequence++
                finalSync = PcControlCommand.GridSwitchSync(
                    sessionId = sessionId,
                    sequence = nextGridSequence,
                    pressedSwitchIds = emptySet()
                )
            } ?: run {
                legacyHeld = legacyHeldSwitchIds.toList().sorted()
            }
            true
        }
        if (!shouldStop) return
        snapshotJob?.cancel()
        snapshotJob = null
        try {
            if (finalSync != null) {
                try {
                    host.send(finalSync)
                } catch (_: Exception) {
                }
            } else {
                legacyHeld.forEach { switchId ->
                    try {
                        host.send(PcControlCommand.GridSwitchSet(switchId, down = false))
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            synchronized(stateLock) {
                if (generation == expectedGeneration) {
                    legacyHeldSwitchIds.clear()
                    mappingByKeyCode = emptyMap()
                    gridSessionId = null
                    nextGridSequence = 0L
                    stoppingGeneration = null
                    _state.value = Grid3ForwardingState()
                }
            }
            val shouldRestoreScanning = synchronized(stateLock) { !destroying }
            if (shouldRestoreScanning) {
                try {
                    host.restoreScanning()
                } finally {
                    host.releaseConnection()
                }
            } else {
                host.releaseConnection()
            }
        }
    }

    private fun enqueueEdge(action: EdgeAction) {
        if (edgeActions.trySend(action).isFailure) {
            stopAfterQueueOverflow()
        }
    }

    private fun stopAfterQueueOverflow() {
        val currentGeneration = synchronized(stateLock) {
            if (!_state.value.active || stoppingGeneration == generation) return
            stoppingGeneration = generation
            activePresses.clear()
            _state.value = _state.value.copy(active = false)
            generation
        }
        snapshotJob?.cancel()
        snapshotJob = null
        forwardingScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val completion = CompletableDeferred<Unit>()
            edgeActions.send(EdgeAction.Stop(currentGeneration, completion))
            completion.await()
        }
    }

    private suspend fun handleConnectionState(connectionState: PcServiceConnectionState) {
        var shouldSynchronize = false
        val currentGeneration = synchronized(stateLock) {
            if (!_state.value.active) return
            when (connectionState) {
                is PcServiceConnectionState.Connected -> {
                    _state.value = _state.value.copy(
                        connectionStatus = Grid3ConnectionStatus.Connected,
                        pcName = connectionState.displayName
                    )
                    shouldSynchronize = gridSessionId != null
                }
                is PcServiceConnectionState.Reconnecting -> {
                    _state.value = _state.value.copy(
                        connectionStatus = Grid3ConnectionStatus.Reconnecting,
                        pcName = connectionState.displayName
                    )
                }
                is PcServiceConnectionState.Failed,
                PcServiceConnectionState.Disconnected -> return@synchronized generation
                else -> Unit
            }
            null
        }
        if (currentGeneration != null) {
            val completion = CompletableDeferred<Unit>()
            edgeActions.send(EdgeAction.Stop(currentGeneration, completion))
            completion.await()
        } else if (shouldSynchronize) {
            enqueueSyncSnapshot()
        }
    }

    private fun updatePressedState() {
        _state.value = _state.value.copy(
            mappings = _state.value.mappings.map { mapping ->
                mapping.copy(pressed = mapping.keyCode.toIntOrNull() in activePresses)
            }
        )
    }

    companion object {
        const val MAX_FORWARDED_SWITCHES = 8
        const val DEFAULT_HOLD_TO_STOP_MS = 5_000L
        const val SNAPSHOT_INTERVAL_MS = 1_000L

        fun sortConfiguredSwitches(switches: List<SwitchEvent>): List<SwitchEvent> {
            return switches.sortedWith { first, second ->
                val firstNumeric = first.code.toIntOrNull()
                val secondNumeric = second.code.toIntOrNull()
                when {
                    firstNumeric != null && secondNumeric != null -> firstNumeric.compareTo(secondNumeric)
                    firstNumeric != null -> -1
                    secondNumeric != null -> 1
                    else -> first.code.compareTo(second.code)
                }
            }
        }
    }
}
