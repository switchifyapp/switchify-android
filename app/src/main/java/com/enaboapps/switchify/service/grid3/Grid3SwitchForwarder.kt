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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        PreferenceManager.PREFERENCE_KEY_HOLD_TO_UNPAUSE_DURATION,
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
        data class SetState(val generation: Long, val keyCode: Int, val switchId: Int, val down: Boolean) :
            ForwardingAction()
        data class RestartPress(val generation: Long, val keyCode: Int, val switchId: Int) :
            ForwardingAction()
        data class Stop(val generation: Long) : ForwardingAction()
    }

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
    private var destroying = false

    init {
        scope.launch {
            for (action in actions) {
                when (action) {
                    is ForwardingAction.SetState -> processSetState(action)
                    is ForwardingAction.RestartPress -> processRestartPress(action)
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
                ForwardingAction.SetState(generation, keyCode, mapping.switchId, down = true)
            } else {
                Grid3SwitchDiagnostics.log(
                    "match action=down result=recover_missing_up keyCode=$keyCode " +
                        "switchId=${mapping.switchId} previousDownTime=${activePress.downTimeMs} " +
                        "downTime=$downTimeMs eventTime=$eventTimeMs"
                )
                ForwardingAction.RestartPress(generation, keyCode, mapping.switchId)
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
                ForwardingAction.SetState(generation, keyCode, mapping.switchId, down = false),
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
                ForwardingAction.SetState(action.generation, action.keyCode, action.switchId, down = false)
            )
            processSetStateLocked(
                ForwardingAction.SetState(action.generation, action.keyCode, action.switchId, down = true)
            )
        }
    }

    private suspend fun processSetStateLocked(action: ForwardingAction.SetState) {
        val active = synchronized(stateLock) {
            _state.value.active && generation == action.generation
        }
        if (!active) return
        val result = try {
            host.send(PcControlCommand.GridSwitchSet(action.switchId, action.down))
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

    private suspend fun stop(expectedGeneration: Long) {
        val shouldStop = synchronized(stateLock) {
            if (!_state.value.active || generation != expectedGeneration) return
            _state.value = _state.value.copy(active = false)
            activePresses.clear()
            true
        }
        if (!shouldStop) return
        try {
            sendMutex.withLock {
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
        } finally {
            synchronized(stateLock) {
                if (generation == expectedGeneration) {
                    remotelyHeld.clear()
                    mappingByKeyCode = emptyMap()
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
        val currentGeneration = synchronized(stateLock) {
            if (!_state.value.active) return
            when (connectionState) {
                is PcServiceConnectionState.Connected -> {
                    _state.value = _state.value.copy(
                        connectionStatus = Grid3ConnectionStatus.Connected,
                        pcName = connectionState.displayName
                    )
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
        const val DEFAULT_HOLD_TO_STOP_MS = 2_000L

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
