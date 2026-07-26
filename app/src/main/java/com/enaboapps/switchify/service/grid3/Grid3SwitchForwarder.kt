package com.enaboapps.switchify.service.grid3

import android.util.Log
import com.enaboapps.switchify.BuildConfig
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal object Grid3SwitchDiagnostics {
    private const val TAG = "Grid3SwitchTrace"

    fun log(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching { Log.d(TAG, message) }
    }
}

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

interface Grid3SwitchInputHandler {
    fun onSwitchPressed(keyCode: Int): Boolean
    fun onSwitchReleased(keyCode: Int): Boolean

    fun onSwitchPressed(keyCode: Int, downTimeMs: Long, eventTimeMs: Long): Boolean {
        return onSwitchPressed(keyCode)
    }

    fun onSwitchReleased(
        keyCode: Int,
        downTimeMs: Long,
        eventTimeMs: Long,
        cancelled: Boolean
    ): Boolean {
        return onSwitchReleased(keyCode)
    }
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
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis
) : Grid3SwitchInputHandler {
    constructor(
        controller: PcServiceConnectionController,
        scanningManager: ScanningManager,
        switchEventProvider: SwitchEventProvider,
        preferenceManager: PreferenceManager,
        scope: CoroutineScope,
        now: () -> Long = System::currentTimeMillis
    ) : this(
        AndroidGrid3ForwardingHost(
            controller,
            scanningManager,
            switchEventProvider,
            preferenceManager
        ),
        scope,
        now
    )

    private sealed class ForwardingAction {
        data class SetState(
            val generation: Long,
            val keyCode: Int,
            val switchId: Int,
            val down: Boolean,
            val delivery: Grid3Delivery?
        ) :
            ForwardingAction()
        data class RestartPress(
            val generation: Long,
            val keyCode: Int,
            val switchId: Int,
            val releaseDelivery: Grid3Delivery?,
            val pressDelivery: Grid3Delivery?
        ) :
            ForwardingAction()
        data class Sync(
            val generation: Long,
            val sessionId: String,
            val sequence: Long,
            val pressedSwitchIds: Set<Int>
        ) : ForwardingAction()
        data class Stop(val generation: Long) : ForwardingAction()
    }

    private data class Grid3Delivery(val sessionId: String, val sequence: Long)
    private data class ActivePress(val downTimeMs: Long)

    private val stateLock = Any()
    private val sendMutex = Mutex()
    private val actions = Channel<ForwardingAction>(Channel.BUFFERED)
    private val _state = MutableStateFlow(Grid3ForwardingState())
    val state: StateFlow<Grid3ForwardingState> = _state
    private var generation = 0L
    private var mappingByKeyCode = emptyMap<Int, Grid3SwitchMapping>()
    private val activePresses = mutableMapOf<Int, ActivePress>()
    private val remotelyHeld = mutableSetOf<Int>()
    private var gridSessionId: String? = null
    private var nextGridSequence = 0L
    private var syncPending = false
    private var snapshotJob: Job? = null
    private var destroying = false

    init {
        scope.launch {
            for (action in actions) {
                when (action) {
                    is ForwardingAction.SetState -> processSetState(action)
                    is ForwardingAction.RestartPress -> processRestartPress(action)
                    is ForwardingAction.Sync -> processSync(action)
                    is ForwardingAction.Stop -> stop(action.generation)
                }
            }
        }
        scope.launch {
            host.connectionState.collect { connectionState ->
                handleConnectionState(connectionState)
            }
        }
    }

    fun start(): Grid3StartResult {
        synchronized(stateLock) {
            if (_state.value.active) return Grid3StartResult.Started
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
            remotelyHeld.clear()
            gridSessionId = if (useSequencedDelivery) UUID.randomUUID().toString() else null
            nextGridSequence = 0L
            syncPending = false
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

    override fun onSwitchPressed(keyCode: Int): Boolean {
        val eventTimeMs = now()
        val downTimeMs = synchronized(stateLock) {
            activePresses[keyCode]?.downTimeMs ?: eventTimeMs
        }
        return onSwitchPressed(keyCode, downTimeMs, eventTimeMs)
    }

    override fun onSwitchPressed(keyCode: Int, downTimeMs: Long, eventTimeMs: Long): Boolean {
        val action = synchronized(stateLock) {
            if (!_state.value.active) {
                Grid3SwitchDiagnostics.log("match action=down result=inactive keyCode=$keyCode")
                return false
            }
            val mapping = mappingByKeyCode[keyCode]
            if (mapping == null) {
                Grid3SwitchDiagnostics.log(
                    "match action=down result=unmapped keyCode=$keyCode downTime=$downTimeMs eventTime=$eventTimeMs"
                )
                return true
            }
            val activePress = activePresses[keyCode]
            if (activePress?.downTimeMs == downTimeMs) {
                Grid3SwitchDiagnostics.log(
                    "match action=down result=repeat keyCode=$keyCode switchId=${mapping.switchId} " +
                        "downTime=$downTimeMs eventTime=$eventTimeMs"
                )
                return true
            }
            activePresses[keyCode] = ActivePress(downTimeMs)
            updatePressedState()
            if (activePress == null) {
                Grid3SwitchDiagnostics.log(
                    "match action=down result=accepted keyCode=$keyCode switchId=${mapping.switchId} " +
                        "downTime=$downTimeMs eventTime=$eventTimeMs"
                )
                ForwardingAction.SetState(
                    generation,
                    keyCode,
                    mapping.switchId,
                    down = true,
                    delivery = nextDeliveryLocked()
                )
            } else {
                Grid3SwitchDiagnostics.log(
                    "match action=down result=recover_missing_up keyCode=$keyCode " +
                        "switchId=${mapping.switchId} previousDownTime=${activePress.downTimeMs} " +
                        "downTime=$downTimeMs eventTime=$eventTimeMs"
                )
                ForwardingAction.RestartPress(
                    generation,
                    keyCode,
                    mapping.switchId,
                    releaseDelivery = nextDeliveryLocked(),
                    pressDelivery = nextDeliveryLocked()
                )
            }
        }
        enqueue(action)
        return true
    }

    override fun onSwitchReleased(keyCode: Int): Boolean {
        val eventTimeMs = now()
        val downTimeMs = synchronized(stateLock) {
            activePresses[keyCode]?.downTimeMs ?: eventTimeMs
        }
        return onSwitchReleased(keyCode, downTimeMs, eventTimeMs, cancelled = false)
    }

    override fun onSwitchReleased(
        keyCode: Int,
        downTimeMs: Long,
        eventTimeMs: Long,
        cancelled: Boolean
    ): Boolean {
        val queued = synchronized(stateLock) {
            if (!_state.value.active) {
                Grid3SwitchDiagnostics.log("match action=up result=inactive keyCode=$keyCode")
                return false
            }
            val mapping = mappingByKeyCode[keyCode]
            if (mapping == null) {
                Grid3SwitchDiagnostics.log(
                    "match action=up result=unmapped keyCode=$keyCode downTime=$downTimeMs " +
                        "eventTime=$eventTimeMs cancelled=$cancelled"
                )
                return true
            }
            val activePress = activePresses[keyCode]
            if (activePress == null) {
                Grid3SwitchDiagnostics.log(
                    "match action=up result=no_active_press keyCode=$keyCode switchId=${mapping.switchId} " +
                        "downTime=$downTimeMs eventTime=$eventTimeMs cancelled=$cancelled"
                )
                return true
            }
            if (activePress.downTimeMs != downTimeMs) {
                Grid3SwitchDiagnostics.log(
                    "match action=up result=stale_sequence keyCode=$keyCode switchId=${mapping.switchId} " +
                        "activeDownTime=${activePress.downTimeMs} downTime=$downTimeMs " +
                        "eventTime=$eventTimeMs cancelled=$cancelled"
                )
                return true
            }
            activePresses.remove(keyCode)
            updatePressedState()
            val durationMs = (eventTimeMs - downTimeMs).coerceAtLeast(0L)
            val shouldStop = !cancelled && durationMs >= _state.value.holdToStopDurationMs
            Grid3SwitchDiagnostics.log(
                "match action=up result=accepted keyCode=$keyCode switchId=${mapping.switchId} " +
                    "downTime=$downTimeMs eventTime=$eventTimeMs duration=$durationMs " +
                    "cancelled=$cancelled stop=$shouldStop"
            )
            Triple(
                ForwardingAction.SetState(
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
        enqueue(queued.first)
        if (queued.second) {
            enqueue(ForwardingAction.Stop(queued.third))
        }
        return true
    }

    suspend fun stop() {
        val currentGeneration = synchronized(stateLock) { generation }
        stop(currentGeneration)
    }

    fun requestStop() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            stop()
        }
    }

    suspend fun destroy() {
        prepareForDestroy()
        stop()
        actions.close()
    }

    fun prepareForDestroy() {
        synchronized(stateLock) {
            destroying = true
        }
    }

    private suspend fun processSetState(action: ForwardingAction.SetState) {
        sendMutex.withLock {
            processSetStateLocked(action)
        }
    }

    private suspend fun processRestartPress(action: ForwardingAction.RestartPress) {
        Grid3SwitchDiagnostics.log(
            "send action=recover_missing_up phase=start keyCode=${action.keyCode} switchId=${action.switchId}"
        )
        sendMutex.withLock {
            processSetStateLocked(
                ForwardingAction.SetState(
                    action.generation,
                    action.keyCode,
                    action.switchId,
                    down = false,
                    delivery = action.releaseDelivery
                )
            )
            processSetStateLocked(
                ForwardingAction.SetState(
                    action.generation,
                    action.keyCode,
                    action.switchId,
                    down = true,
                    delivery = action.pressDelivery
                )
            )
        }
    }

    private suspend fun processSetStateLocked(action: ForwardingAction.SetState) {
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
        val resultName = when (result) {
            PcCommandResult.Ack -> "ack"
            is PcCommandResult.AuthFailed -> "auth_failed"
            is PcCommandResult.Failed -> "failed"
        }
        Grid3SwitchDiagnostics.log(
            "send action=${if (action.down) "down" else "up"} keyCode=${action.keyCode} " +
                "switchId=${action.switchId} result=$resultName"
        )
        synchronized(stateLock) {
            if (generation != action.generation) return@synchronized
            when {
                result == PcCommandResult.Ack && action.down -> remotelyHeld += action.switchId
                result == PcCommandResult.Ack && !action.down -> remotelyHeld -= action.switchId
            }
        }
    }

    private suspend fun processSync(action: ForwardingAction.Sync) {
        try {
            sendMutex.withLock {
                val active = synchronized(stateLock) {
                    _state.value.active && generation == action.generation
                }
                if (!active) return
                val result = try {
                    host.send(
                        PcControlCommand.GridSwitchSync(
                            sessionId = action.sessionId,
                            sequence = action.sequence,
                            pressedSwitchIds = action.pressedSwitchIds
                        )
                    )
                } catch (error: Exception) {
                    PcCommandResult.Failed(error.message ?: "Could not synchronize switch state.")
                }
                Grid3SwitchDiagnostics.log(
                    "send action=sync sequence=${action.sequence} " +
                        "pressed=${action.pressedSwitchIds.sorted()} result=${resultName(result)}"
                )
                if (result == PcCommandResult.Ack) {
                    synchronized(stateLock) {
                        if (generation == action.generation) {
                            remotelyHeld.clear()
                            remotelyHeld.addAll(action.pressedSwitchIds)
                        }
                    }
                }
            }
        } finally {
            synchronized(stateLock) {
                if (generation == action.generation) {
                    syncPending = false
                }
            }
        }
    }

    private fun nextDeliveryLocked(): Grid3Delivery? {
        val sessionId = gridSessionId ?: return null
        nextGridSequence++
        return Grid3Delivery(sessionId, nextGridSequence)
    }

    private fun createSyncActionLocked(): ForwardingAction.Sync? {
        val sessionId = gridSessionId ?: return null
        if (!_state.value.active || syncPending) return null
        nextGridSequence++
        syncPending = true
        val pressedSwitchIds = activePresses.keys.mapNotNull { keyCode ->
            mappingByKeyCode[keyCode]?.switchId
        }.toSet()
        return ForwardingAction.Sync(
            generation = generation,
            sessionId = sessionId,
            sequence = nextGridSequence,
            pressedSwitchIds = pressedSwitchIds
        )
    }

    private fun enqueueSyncSnapshot() {
        val action = synchronized(stateLock) { createSyncActionLocked() } ?: return
        enqueue(action)
    }

    private fun startSnapshotLoop() {
        snapshotJob?.cancel()
        snapshotJob = scope.launch {
            while (true) {
                delay(SNAPSHOT_INTERVAL_MS)
                val active = synchronized(stateLock) { _state.value.active }
                if (!active) return@launch
                enqueueSyncSnapshot()
            }
        }
    }

    private fun resultName(result: PcCommandResult): String {
        return when (result) {
            PcCommandResult.Ack -> "ack"
            is PcCommandResult.AuthFailed -> "auth_failed"
            is PcCommandResult.Failed -> "failed"
        }
    }

    private suspend fun stop(expectedGeneration: Long) {
        var finalSync: PcControlCommand.GridSwitchSync? = null
        val shouldStop = synchronized(stateLock) {
            if (!_state.value.active || generation != expectedGeneration) return
            _state.value = _state.value.copy(active = false)
            activePresses.clear()
            gridSessionId?.let { sessionId ->
                nextGridSequence++
                finalSync = PcControlCommand.GridSwitchSync(
                    sessionId = sessionId,
                    sequence = nextGridSequence,
                    pressedSwitchIds = emptySet()
                )
            }
            true
        }
        if (!shouldStop) return
        snapshotJob?.cancel()
        snapshotJob = null
        try {
            sendMutex.withLock {
                val syncResult = finalSync?.let { command ->
                    try {
                        host.send(command)
                    } catch (error: Exception) {
                        PcCommandResult.Failed(error.message ?: "Could not synchronize switch state.")
                    }
                }
                if (syncResult != PcCommandResult.Ack) {
                    val held = synchronized(stateLock) {
                        if (generation != expectedGeneration) emptyList() else remotelyHeld.toList().sorted()
                    }
                    held.forEach { switchId ->
                        try {
                            host.send(PcControlCommand.GridSwitchSet(switchId, down = false))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } finally {
            synchronized(stateLock) {
                if (generation == expectedGeneration) {
                    remotelyHeld.clear()
                    mappingByKeyCode = emptyMap()
                    gridSessionId = null
                    nextGridSequence = 0L
                    syncPending = false
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

    private fun enqueue(action: ForwardingAction) {
        if (actions.trySend(action).isFailure) {
            requestStop()
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
                    remotelyHeld.clear()
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
            stop(currentGeneration)
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
