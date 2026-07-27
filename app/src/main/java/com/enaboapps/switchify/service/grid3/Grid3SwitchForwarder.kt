package com.enaboapps.switchify.service.grid3

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcProtocol
import com.enaboapps.switchify.pc.PcProfileCatalogResult
import com.enaboapps.switchify.pc.PcSwitchBindingSummary
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class Grid3SwitchMapping(
    val keyCode: String,
    val name: String,
    val switchId: Int,
    val pressed: Boolean = false,
    val outputLabel: String = "Grid switch $switchId"
)

enum class Grid3ConnectionStatus {
    Connected,
    Reconnecting
}

data class Grid3ForwardingState(
    val active: Boolean = false,
    val connectionStatus: Grid3ConnectionStatus = Grid3ConnectionStatus.Connected,
    val pcName: String? = null,
    val profileName: String? = null,
    val mappings: List<Grid3SwitchMapping> = emptyList(),
    val overflowSwitches: List<String> = emptyList(),
    val holdToStopDurationMs: Long = Grid3SwitchForwarder.DEFAULT_HOLD_TO_STOP_MS
)

sealed class Grid3StartResult {
    data object Started : Grid3StartResult()
    data object NoExternalSwitches : Grid3StartResult()
    data object UnsupportedPc : Grid3StartResult()
    data object ProfileChanged : Grid3StartResult()
    data class Failed(val message: String) : Grid3StartResult()
}

sealed class PcSwitchCatalogResult {
    data class Loaded(val catalog: PcSwitchProfileCatalog) : PcSwitchCatalogResult()
    data class Failed(val message: String) : PcSwitchCatalogResult()
    data object Unsupported : PcSwitchCatalogResult()
}

internal interface Grid3ForwardingHost {
    val connectionState: StateFlow<PcServiceConnectionState>
    fun currentPointerProfile(): PcPointerMovementProfile?
    fun currentPcName(): String?
    fun currentPcId(): String? = null
    fun configuredSwitches(): List<SwitchEvent>
    fun holdToStopDurationMs(): Long
    fun suspendScanning()
    fun restoreScanning()
    fun maintainConnection()
    fun releaseConnection()
    suspend fun send(command: PcControlCommand): PcCommandResult
    suspend fun requestProfileCatalog(): PcProfileCatalogResult = PcProfileCatalogResult.Failed()
    suspend fun sendRealtime(command: PcControlCommand): PcCommandResult = send(command)
}

internal interface Grid3SwitchInputHandler {
    val forwardingActivation: Long
        get() = 0L

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
    override fun currentPcId() = controller.currentControlDesktopId()
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
    override suspend fun requestProfileCatalog() = controller.requestSwitchProfileCatalog()
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
    private val sessionLifecycleMutex = Mutex()
    private val edgeActions = Channel<EdgeAction>(Channel.BUFFERED)
    private val snapshotActions = Channel<SnapshotAction>(Channel.CONFLATED)
    private val _state = MutableStateFlow(Grid3ForwardingState())
    val state: StateFlow<Grid3ForwardingState> = _state
    private var generation = 0L
    private var mappingByKeyCode = emptyMap<Int, Grid3SwitchMapping>()
    private val activePresses = mutableMapOf<Int, ActivePress>()
    private val legacyHeldSwitchIds = mutableSetOf<Int>()
    private var gridSessionId: String? = null
    private var genericProfile: PcSwitchProfileSummary? = null
    private var useGenericProtocol = false
    private var sessionReady = false
    private var awaitingReconnect = false
    private var nextGridSequence = 0L
    private var snapshotJob: Job? = null
    private var stoppingGeneration: Long? = null
    private var startingGeneration: Long? = null
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
        val gridProfile = PcSwitchProfileSummary(
            id = "builtin.grid3",
            version = 1,
            name = "Grid 3",
            kind = "grid3",
            bindings = (1..8).map { PcSwitchBindingSummary(it, "Grid switch $it", "stateful") }
        )
        return startPrepared(gridProfile, generic = false)
    }

    suspend fun loadProfileCatalog(): PcSwitchCatalogResult {
        val profile = host.currentPointerProfile() ?: return PcSwitchCatalogResult.Unsupported
        val commands = profile.capabilities.supportedCommands
        val genericSupported = GENERIC_COMMANDS.all(commands::contains)
        if (genericSupported) {
            return when (val result = host.requestProfileCatalog()) {
                is PcProfileCatalogResult.Loaded -> PcSwitchCatalogResult.Loaded(result.catalog)
                is PcProfileCatalogResult.Failed -> PcSwitchCatalogResult.Failed(result.message)
                is PcProfileCatalogResult.AuthFailed -> PcSwitchCatalogResult.Failed(result.message)
            }
        }
        if (PcProtocol.GRID_SWITCH_SET_COMMAND in commands) {
            return PcSwitchCatalogResult.Loaded(
                PcSwitchProfileCatalog(
                    catalogRevision = 0,
                    profiles = listOf(
                        PcSwitchProfileSummary(
                            id = "builtin.grid3",
                            version = 1,
                            name = "Grid 3",
                            kind = "grid3",
                            bindings = (1..8).map {
                                PcSwitchBindingSummary(it, "Grid switch $it", "stateful")
                            }
                        )
                    ),
                    legacy = true
                )
            )
        }
        return PcSwitchCatalogResult.Unsupported
    }

    fun currentPcId(): String = host.currentPcId() ?: host.currentPcName() ?: "default"

    fun configuredExternalSwitchCount(): Int =
        host.configuredSwitches().count { it.type == SWITCH_EVENT_TYPE_EXTERNAL }

    suspend fun start(profile: PcSwitchProfileSummary, legacy: Boolean): Grid3StartResult {
        return sessionLifecycleMutex.withLock {
            synchronized(stateLock) {
                if (_state.value.active || stoppingGeneration != null) {
                    return@withLock Grid3StartResult.Started
                }
            }
            if (legacy) return@withLock startPrepared(profile, generic = false)
            val externalSwitches = host.configuredSwitches()
                .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
            if (externalSwitches.isEmpty()) {
                return@withLock Grid3StartResult.NoExternalSwitches
            }
            val sessionId = UUID.randomUUID().toString()
            val startGeneration = synchronized(stateLock) {
                generation++
                generation.also { startingGeneration = it }
            }
            val startResult = try {
                host.send(
                    PcControlCommand.SwitchSessionStart(
                        sessionId = sessionId,
                        profileId = profile.id,
                        profileVersion = profile.version,
                        switchCount = externalSwitches.size.coerceAtMost(MAX_FORWARDED_SWITCHES)
                    )
                )
            } catch (error: Exception) {
                PcCommandResult.Failed(error.message ?: "PC Switch Control could not start.")
            }
            if (startResult != PcCommandResult.Ack) {
                synchronized(stateLock) {
                    if (startingGeneration == startGeneration) startingGeneration = null
                }
                return@withLock if (startResult is PcCommandResult.Failed &&
                    startResult.code == "profile_changed"
                ) {
                    Grid3StartResult.ProfileChanged
                } else {
                    Grid3StartResult.Failed(
                        (startResult as? PcCommandResult.Failed)?.message
                            ?: "PC Switch Control could not start."
                    )
                }
            }
            val prepared = startPrepared(
                profile,
                generic = true,
                existingSessionId = sessionId,
                reservedGeneration = startGeneration
            )
            if (prepared != Grid3StartResult.Started) {
                synchronized(stateLock) {
                    if (startingGeneration == startGeneration) startingGeneration = null
                }
                try {
                    host.send(PcControlCommand.SwitchSessionStop(sessionId, 1L))
                } catch (_: Exception) {
                }
            }
            prepared
        }
    }

    private fun startPrepared(
        selectedProfile: PcSwitchProfileSummary,
        generic: Boolean,
        existingSessionId: String? = null,
        reservedGeneration: Long? = null
    ): Grid3StartResult {
        synchronized(stateLock) {
            if (_state.value.active || stoppingGeneration != null) return Grid3StartResult.Started
        }
        val profile = host.currentPointerProfile() ?: return Grid3StartResult.UnsupportedPc
        if (!generic &&
            profile.capabilities.supportedCommands.contains(PcProtocol.GRID_SWITCH_SET_COMMAND) != true
        ) {
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
            generic ||
                (profile.capabilities.supportedCommands.contains(PcProtocol.GRID_SWITCH_SYNC_COMMAND) &&
                    profile.capabilities.noAckCommands.contains(PcProtocol.GRID_SWITCH_SET_COMMAND))
        val mappings = forwarded.mapIndexed { index, switchEvent ->
            Grid3SwitchMapping(
                keyCode = switchEvent.code,
                name = switchEvent.name,
                switchId = index + 1,
                outputLabel = selectedProfile.bindings
                    .firstOrNull { it.switchId == index + 1 }
                    ?.label
                    ?: "Unassigned"
            )
        }
        synchronized(stateLock) {
            if (reservedGeneration != null) {
                generation = reservedGeneration
            } else {
                generation++
            }
            startingGeneration = null
            mappingByKeyCode = mappings.mapNotNull { mapping ->
                mapping.keyCode.toIntOrNull()?.let { it to mapping }
            }.toMap()
            activePresses.clear()
            legacyHeldSwitchIds.clear()
            gridSessionId = existingSessionId ?: if (useSequencedDelivery) UUID.randomUUID().toString() else null
            genericProfile = selectedProfile
            useGenericProtocol = generic
            sessionReady = true
            awaitingReconnect = false
            nextGridSequence = 0L
            stoppingGeneration = null
            _state.value = Grid3ForwardingState(
                active = true,
                pcName = host.currentPcName(),
                profileName = selectedProfile.name,
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

    override val forwardingActivation: Long
        get() = synchronized(stateLock) { generation }

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
        val targetGeneration = synchronized(stateLock) {
            startingGeneration ?: generation.takeIf { _state.value.active }
        } ?: return
        val completion = CompletableDeferred<Unit>()
        edgeActions.send(EdgeAction.Stop(targetGeneration, completion))
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
            _state.value.active && generation == action.generation && sessionReady
        }
        if (!active) return
        val command = if (useGenericProtocol && action.delivery != null) {
            PcControlCommand.SwitchEdge(
                sessionId = action.delivery.sessionId,
                sequence = action.delivery.sequence,
                switchId = action.switchId,
                down = action.down
            )
        } else {
            PcControlCommand.GridSwitchSet(
                switchId = action.switchId,
                down = action.down,
                sessionId = action.delivery?.sessionId,
                sequence = action.delivery?.sequence
            )
        }
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
            _state.value.active && generation == action.generation && sessionReady
        }
        if (!active) return
        try {
            host.send(
                if (useGenericProtocol) PcControlCommand.SwitchSync(
                    sessionId = action.sessionId,
                    sequence = action.sequence,
                    pressedSwitchIds = action.pressedSwitchIds
                ) else PcControlCommand.GridSwitchSync(
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

    private suspend fun processStop(expectedGeneration: Long) = sessionLifecycleMutex.withLock {
        var finalCommand: PcControlCommand? = null
        var legacyHeld = emptyList<Int>()
        val shouldStop = synchronized(stateLock) {
            if (
                generation != expectedGeneration ||
                (!_state.value.active && stoppingGeneration != expectedGeneration)
            ) {
                false
            } else {
                stoppingGeneration = expectedGeneration
                _state.value = _state.value.copy(active = false)
                activePresses.clear()
                gridSessionId?.let { sessionId ->
                    nextGridSequence++
                    finalCommand = if (useGenericProtocol) {
                        PcControlCommand.SwitchSessionStop(sessionId, nextGridSequence)
                    } else {
                        PcControlCommand.GridSwitchSync(sessionId, nextGridSequence, emptySet())
                    }
                } ?: run {
                    legacyHeld = legacyHeldSwitchIds.toList().sorted()
                }
                true
            }
        }
        if (!shouldStop) return@withLock
        snapshotJob?.cancel()
        snapshotJob = null
        try {
            if (finalCommand != null) {
                try {
                    host.send(finalCommand)
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
                    genericProfile = null
                    useGenericProtocol = false
                    sessionReady = false
                    awaitingReconnect = false
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
        var profileToRestart: PcSwitchProfileSummary? = null
        var restartedSessionId: String? = null
        var restartedGeneration: Long? = null
        val currentGeneration = synchronized(stateLock) {
            if (!_state.value.active) return
            when (connectionState) {
                is PcServiceConnectionState.Connected -> {
                    if (useGenericProtocol && awaitingReconnect) {
                        restartedSessionId = UUID.randomUUID().toString()
                        gridSessionId = restartedSessionId
                        nextGridSequence = 0L
                        sessionReady = false
                        awaitingReconnect = false
                        profileToRestart = genericProfile
                        restartedGeneration = generation
                        _state.value = _state.value.copy(
                            connectionStatus = Grid3ConnectionStatus.Reconnecting,
                            pcName = connectionState.displayName
                        )
                    } else {
                        sessionReady = true
                        _state.value = _state.value.copy(
                            connectionStatus = Grid3ConnectionStatus.Connected,
                            pcName = connectionState.displayName
                        )
                        shouldSynchronize = gridSessionId != null
                    }
                }
                is PcServiceConnectionState.Reconnecting -> {
                    sessionReady = false
                    awaitingReconnect = true
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
        } else if (profileToRestart != null && restartedSessionId != null) {
            val restartSucceeded = sessionLifecycleMutex.withLock {
                val restartIsCurrent = synchronized(stateLock) {
                    _state.value.active &&
                        generation == restartedGeneration &&
                        gridSessionId == restartedSessionId &&
                        genericProfile == profileToRestart &&
                        stoppingGeneration == null
                }
                if (!restartIsCurrent) return@withLock true
                val result = host.send(
                    PcControlCommand.SwitchSessionStart(
                        sessionId = restartedSessionId,
                        profileId = profileToRestart.id,
                        profileVersion = profileToRestart.version,
                        switchCount = synchronized(stateLock) { mappingByKeyCode.size }
                    )
                )
                if (result != PcCommandResult.Ack) return@withLock false
                synchronized(stateLock) {
                    if (
                        _state.value.active &&
                        generation == restartedGeneration &&
                        gridSessionId == restartedSessionId &&
                        stoppingGeneration == null
                    ) {
                        sessionReady = true
                        _state.value = _state.value.copy(
                            connectionStatus = Grid3ConnectionStatus.Connected
                        )
                        true
                    } else {
                        false
                    }
                }
            }
            if (restartSucceeded) {
                enqueueSyncSnapshot()
            } else {
                val completion = CompletableDeferred<Unit>()
                edgeActions.send(EdgeAction.Stop(synchronized(stateLock) { generation }, completion))
                completion.await()
            }
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
        val GENERIC_COMMANDS = setOf(
            PcProtocol.SWITCH_PROFILE_LIST_COMMAND,
            PcProtocol.SWITCH_SESSION_START_COMMAND,
            PcProtocol.SWITCH_EDGE_COMMAND,
            PcProtocol.SWITCH_SYNC_COMMAND,
            PcProtocol.SWITCH_SESSION_STOP_COMMAND
        )

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
