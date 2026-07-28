package com.enaboapps.switchify.service.pcswitchcontrol

import com.enaboapps.switchify.R
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
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class PcSwitchControlMapping(
    val keyCode: String,
    val name: String,
    val switchId: Int,
    val pressed: Boolean = false,
    val outputLabel: String? = null
)

enum class PcSwitchControlConnectionStatus {
    Connected,
    Reconnecting
}

data class PcSwitchControlState(
    val active: Boolean = false,
    val connectionStatus: PcSwitchControlConnectionStatus = PcSwitchControlConnectionStatus.Connected,
    val pcName: String? = null,
    val profileName: String? = null,
    val mappings: List<PcSwitchControlMapping> = emptyList(),
    val overflowSwitches: List<String> = emptyList(),
    val holdToStopDurationMs: Long = PcSwitchControlForwarder.DEFAULT_HOLD_TO_STOP_MS
)

sealed class PcSwitchControlStartResult {
    data object Started : PcSwitchControlStartResult()
    data object NoExternalSwitches : PcSwitchControlStartResult()
    data object UnsupportedPc : PcSwitchControlStartResult()
    data object ProfileChanged : PcSwitchControlStartResult()
    data class Failed(val message: String) : PcSwitchControlStartResult()
}

sealed class PcSwitchCatalogResult {
    data class Loaded(val catalog: PcSwitchProfileCatalog) : PcSwitchCatalogResult()
    data class Failed(val message: String) : PcSwitchCatalogResult()
    data object Unsupported : PcSwitchCatalogResult()
}

internal enum class PcSwitchControlExitReason {
    InactivityTimeout
}

internal interface PcSwitchControlHost {
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
    fun showInactivityTimeout() {}
    suspend fun send(command: PcControlCommand): PcCommandResult
    suspend fun requestProfileCatalog(): PcProfileCatalogResult = PcProfileCatalogResult.Failed()
    suspend fun sendRealtime(command: PcControlCommand): PcCommandResult = send(command)
}

internal interface PcSwitchControlInputHandler {
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

private class AndroidPcSwitchControlHost(
    private val controller: PcServiceConnectionController,
    private val scanningManager: ScanningManager,
    private val switchEventProvider: SwitchEventProvider,
    private val preferenceManager: PreferenceManager
) : PcSwitchControlHost {
    override val connectionState: StateFlow<PcServiceConnectionState> = controller.state
    override fun currentPointerProfile() = controller.currentPointerProfile()
    override fun currentPcName() = controller.currentControlDeviceName()
    override fun currentPcId() = controller.currentControlDesktopId()
    override fun configuredSwitches() = switchEventProvider.externalSwitches()
    override fun holdToStopDurationMs() = preferenceManager.getLongValue(
        PreferenceManager.PREFERENCE_KEY_PC_SWITCH_CONTROL_HOLD_TO_STOP_DURATION,
        PcSwitchControlForwarder.DEFAULT_HOLD_TO_STOP_MS
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
    override fun showInactivityTimeout() {
        ServiceMessageHUD.instance.showMessage(
            R.string.pc_switch_inactivity_timeout,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            ServiceMessageHUD.Time.LONG,
            severity = MessageSeverity.Info
        )
    }
    override suspend fun send(command: PcControlCommand) = controller.sendControlCommand(command)
    override suspend fun requestProfileCatalog() = controller.requestSwitchProfileCatalog()
    override suspend fun sendRealtime(command: PcControlCommand) =
        controller.sendRealtimeControlCommand(command)
}

class PcSwitchControlForwarder internal constructor(
    private val host: PcSwitchControlHost,
    scope: CoroutineScope,
    private val inactivityTimeoutMs: Long = INACTIVITY_TIMEOUT_MS
) : PcSwitchControlInputHandler {
    private enum class StopPolicy(
        val restoreScanning: Boolean,
        val releaseConnection: Boolean
    ) {
        Normal(restoreScanning = true, releaseConnection = true),
        ChangeProfile(restoreScanning = true, releaseConnection = false),
        ScreenSleep(restoreScanning = false, releaseConnection = true),
        Destroy(restoreScanning = false, releaseConnection = true)
    }

    constructor(
        controller: PcServiceConnectionController,
        scanningManager: ScanningManager,
        switchEventProvider: SwitchEventProvider,
        preferenceManager: PreferenceManager,
        scope: CoroutineScope
    ) : this(
        AndroidPcSwitchControlHost(
            controller,
            scanningManager,
            switchEventProvider,
            preferenceManager
        ),
        scope,
        INACTIVITY_TIMEOUT_MS
    )

    private sealed class EdgeAction {
        data class SetState(
            val generation: Long,
            val keyCode: Int,
            val switchId: Int,
            val down: Boolean,
            val delivery: SequencedDelivery?
        ) : EdgeAction()

        data class RestartPress(
            val generation: Long,
            val keyCode: Int,
            val switchId: Int,
            val releaseDelivery: SequencedDelivery?,
            val pressDelivery: SequencedDelivery?
        ) : EdgeAction()

        data class Stop(
            val generation: Long,
            val policy: StopPolicy = StopPolicy.Normal,
            val releaseWhenInactive: Boolean = false,
            val connectionEpisodeId: String? = null,
            val exitReason: PcSwitchControlExitReason? = null,
            val completion: CompletableDeferred<Unit>? = null
        ) : EdgeAction()
    }

    private data class SnapshotAction(
        val generation: Long,
        val sessionId: String,
        val sequence: Long,
        val pressedSwitchIds: Set<Int>
    )

    private data class SequencedDelivery(val sessionId: String, val sequence: Long)
    private data class ActivePress(val downTimeMs: Long)

    private val forwardingJob = SupervisorJob()
    private val forwardingScope = CoroutineScope(
        scope.coroutineContext.minusKey(Job) + forwardingJob
    )
    private val stateLock = Any()
    private val sessionLifecycleMutex = Mutex()
    private val edgeActions = Channel<EdgeAction>(Channel.BUFFERED)
    private val snapshotActions = Channel<SnapshotAction>(Channel.CONFLATED)
    private val _state = MutableStateFlow(PcSwitchControlState())
    val state: StateFlow<PcSwitchControlState> = _state
    private val _terminalExitReason = MutableStateFlow<PcSwitchControlExitReason?>(null)
    internal val terminalExitReason: StateFlow<PcSwitchControlExitReason?> =
        _terminalExitReason.asStateFlow()
    private var generation = 0L
    private var mappingByKeyCode = emptyMap<Int, PcSwitchControlMapping>()
    private val activePresses = mutableMapOf<Int, ActivePress>()
    private val legacyHeldSwitchIds = mutableSetOf<Int>()
    private var switchSessionId: String? = null
    private var genericProfile: PcSwitchProfileSummary? = null
    private var useGenericProtocol = false
    private var sessionReady = false
    private var awaitingReconnect = false
    private var nextSequence = 0L
    private var snapshotJob: Job? = null
    private var inactivityTimeoutJob: Job? = null
    private var stoppingGeneration: Long? = null
    private var startingGeneration: Long? = null
    private var connectionEpisodeId: String? = null
    private var destroying = false

    init {
        forwardingScope.launch {
            for (action in edgeActions) {
                when (action) {
                    is EdgeAction.SetState -> processSetState(action)
                    is EdgeAction.RestartPress -> processRestartPress(action)
                    is EdgeAction.Stop -> {
                        try {
                            processStop(
                                action.generation,
                                action.policy,
                                action.releaseWhenInactive,
                                action.connectionEpisodeId,
                                action.exitReason
                            )
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

    fun startLegacyGridProfile(): PcSwitchControlStartResult {
        val legacyGridProfile = PcSwitchProfileSummary(
            id = "builtin.grid3",
            version = 1,
            name = "Grid 3",
            kind = "grid3",
            bindings = (1..8).map { PcSwitchBindingSummary(it, "Grid switch $it", "stateful") }
        )
        return startPrepared(legacyGridProfile, usesGenericProtocol = false)
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
        if (PcProtocol.LEGACY_GRID_SWITCH_SET_COMMAND in commands) {
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
                    usesLegacyGridProtocol = true
                )
            )
        }
        return PcSwitchCatalogResult.Unsupported
    }

    fun currentPcId(): String = host.currentPcId() ?: host.currentPcName() ?: "default"

    fun configuredExternalSwitchCount(): Int =
        host.configuredSwitches().count { it.type == SWITCH_EVENT_TYPE_EXTERNAL }

    internal fun acquireConnectionForEpisode(episodeId: String) {
        val shouldMaintain = synchronized(stateLock) {
            if (connectionEpisodeId == episodeId) {
                false
            } else {
                connectionEpisodeId = episodeId
                _terminalExitReason.value = null
                true
            }
        }
        if (shouldMaintain) {
            host.maintainConnection()
        }
    }

    internal fun acknowledgeTerminalExit(reason: PcSwitchControlExitReason) {
        _terminalExitReason.compareAndSet(reason, null)
    }

    suspend fun start(
        profile: PcSwitchProfileSummary,
        usesLegacyGridProtocol: Boolean
    ): PcSwitchControlStartResult {
        return sessionLifecycleMutex.withLock {
            synchronized(stateLock) {
                if (_state.value.active || stoppingGeneration != null) {
                    return@withLock PcSwitchControlStartResult.Started
                }
            }
            if (usesLegacyGridProtocol) return@withLock startPrepared(profile, usesGenericProtocol = false)
            val externalSwitches = host.configuredSwitches()
                .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
            if (externalSwitches.isEmpty()) {
                return@withLock PcSwitchControlStartResult.NoExternalSwitches
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
                    PcSwitchControlStartResult.ProfileChanged
                } else {
                    PcSwitchControlStartResult.Failed(
                        (startResult as? PcCommandResult.Failed)?.message
                            ?: "PC Switch Control could not start."
                    )
                }
            }
            val prepared = startPrepared(
                profile,
                usesGenericProtocol = true,
                existingSessionId = sessionId,
                reservedGeneration = startGeneration
            )
            if (prepared != PcSwitchControlStartResult.Started) {
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
        usesGenericProtocol: Boolean,
        existingSessionId: String? = null,
        reservedGeneration: Long? = null
    ): PcSwitchControlStartResult {
        synchronized(stateLock) {
            if (_state.value.active || stoppingGeneration != null) return PcSwitchControlStartResult.Started
        }
        val profile = host.currentPointerProfile() ?: return PcSwitchControlStartResult.UnsupportedPc
        if (!usesGenericProtocol &&
            profile.capabilities.supportedCommands.contains(PcProtocol.LEGACY_GRID_SWITCH_SET_COMMAND) != true
        ) {
            return PcSwitchControlStartResult.UnsupportedPc
        }
        val externalSwitches = host.configuredSwitches()
            .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
        if (externalSwitches.isEmpty()) {
            return PcSwitchControlStartResult.NoExternalSwitches
        }

        val sorted = sortConfiguredSwitches(externalSwitches)
        val forwarded = sorted.take(MAX_FORWARDED_SWITCHES)
        val holdToStopDurationMs = host.holdToStopDurationMs()
        val useSequencedDelivery =
            usesGenericProtocol ||
                (profile.capabilities.supportedCommands.contains(PcProtocol.LEGACY_GRID_SWITCH_SYNC_COMMAND) &&
                    profile.capabilities.noAckCommands.contains(PcProtocol.LEGACY_GRID_SWITCH_SET_COMMAND))
        val mappings = forwarded.mapIndexed { index, switchEvent ->
            PcSwitchControlMapping(
                keyCode = switchEvent.code,
                name = switchEvent.name,
                switchId = index + 1,
                outputLabel = selectedProfile.bindings
                    .firstOrNull {
                        it.switchId == index + 1 && it.behavior != "unassigned"
                    }
                    ?.label
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
            switchSessionId = existingSessionId ?: if (useSequencedDelivery) UUID.randomUUID().toString() else null
            genericProfile = selectedProfile
            useGenericProtocol = usesGenericProtocol
            sessionReady = true
            awaitingReconnect = false
            nextSequence = 0L
            stoppingGeneration = null
            _state.value = PcSwitchControlState(
                active = true,
                pcName = host.currentPcName(),
                profileName = selectedProfile.name,
                mappings = mappings,
                overflowSwitches = sorted.drop(MAX_FORWARDED_SWITCHES).map { it.name },
                holdToStopDurationMs = holdToStopDurationMs
            )
        }
        host.suspendScanning()
        ensureConnectionOwned()
        if (useSequencedDelivery) {
            enqueueSyncSnapshot()
            startSnapshotLoop()
        }
        resetInactivityTimeout()
        return PcSwitchControlStartResult.Started
    }

    private fun ensureConnectionOwned() {
        val shouldMaintain = synchronized(stateLock) {
            if (connectionEpisodeId != null) {
                false
            } else {
                connectionEpisodeId = UUID.randomUUID().toString()
                _terminalExitReason.value = null
                true
            }
        }
        if (shouldMaintain) {
            host.maintainConnection()
        }
    }

    override val forwardingActivation: Long
        get() = synchronized(stateLock) { generation }

    override fun onSwitchPressed(keyCode: Int, downTimeMs: Long, eventTimeMs: Long): Boolean {
        resetInactivityTimeout()
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
        resetInactivityTimeout()
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
            enqueueEdge(
                EdgeAction.Stop(
                    generation = queued.third,
                    policy = StopPolicy.ChangeProfile
                )
            )
        }
        return true
    }

    suspend fun stop() {
        stop(StopPolicy.Normal)
    }

    private suspend fun stop(policy: StopPolicy) {
        val stopTarget = synchronized(stateLock) {
            val targetGeneration =
                startingGeneration ?: generation.takeIf { _state.value.active }
            targetGeneration?.let { it to connectionEpisodeId }
        } ?: return
        val completion = CompletableDeferred<Unit>()
        edgeActions.send(
            EdgeAction.Stop(
                generation = stopTarget.first,
                policy = policy,
                connectionEpisodeId = stopTarget.second,
                completion = completion
            )
        )
        completion.await()
    }

    fun requestStop() {
        requestStop(StopPolicy.Normal, releaseWhenInactive = false)
    }

    fun requestChangeProfile() {
        requestStop(StopPolicy.ChangeProfile, releaseWhenInactive = false)
    }

    fun requestClose(episodeId: String? = null) {
        requestStop(
            StopPolicy.Normal,
            releaseWhenInactive = true,
            requestedEpisodeId = episodeId
        )
    }

    fun requestCloseForScreenSleep(episodeId: String? = null) {
        requestStop(
            StopPolicy.ScreenSleep,
            releaseWhenInactive = true,
            requestedEpisodeId = episodeId
        )
    }

    private fun requestStop(
        policy: StopPolicy,
        releaseWhenInactive: Boolean,
        requestedEpisodeId: String? = null
    ) {
        val stopTarget = synchronized(stateLock) {
            val targetGeneration =
                startingGeneration ?: generation.takeIf { _state.value.active } ?: generation
            targetGeneration to (requestedEpisodeId ?: connectionEpisodeId)
        }
        enqueueEdge(
            EdgeAction.Stop(
                generation = stopTarget.first,
                policy = policy,
                releaseWhenInactive = releaseWhenInactive,
                connectionEpisodeId = stopTarget.second
            )
        )
    }

    suspend fun destroy() {
        prepareForDestroy()
        try {
            stop(StopPolicy.Destroy)
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
            PcControlCommand.LegacyGridSwitchSet(
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
                ) else PcControlCommand.LegacyGridSwitchSync(
                    sessionId = action.sessionId,
                    sequence = action.sequence,
                    pressedSwitchIds = action.pressedSwitchIds
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun nextDeliveryLocked(): SequencedDelivery? {
        val sessionId = switchSessionId ?: return null
        nextSequence++
        return SequencedDelivery(sessionId, nextSequence)
    }

    private fun createSnapshotActionLocked(): SnapshotAction? {
        val sessionId = switchSessionId ?: return null
        if (!_state.value.active) return null
        nextSequence++
        val pressedSwitchIds = activePresses.keys.mapNotNull { keyCode ->
            mappingByKeyCode[keyCode]?.switchId
        }.toSet()
        return SnapshotAction(
            generation = generation,
            sessionId = sessionId,
            sequence = nextSequence,
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

    private fun resetInactivityTimeout() {
        synchronized(stateLock) {
            if (!_state.value.active) return
            val expectedGeneration = generation
            val expectedConnectionEpisodeId = connectionEpisodeId
            inactivityTimeoutJob?.cancel()
            inactivityTimeoutJob = forwardingScope.launch {
                delay(inactivityTimeoutMs)
                edgeActions.send(
                    EdgeAction.Stop(
                        generation = expectedGeneration,
                        policy = StopPolicy.Normal,
                        connectionEpisodeId = expectedConnectionEpisodeId,
                        exitReason = PcSwitchControlExitReason.InactivityTimeout
                    )
                )
            }
        }
    }

    private suspend fun processStop(
        expectedGeneration: Long,
        policy: StopPolicy,
        releaseWhenInactive: Boolean,
        expectedConnectionEpisodeId: String?,
        exitReason: PcSwitchControlExitReason?
    ) = sessionLifecycleMutex.withLock {
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
                switchSessionId?.let { sessionId ->
                    nextSequence++
                    finalCommand = if (useGenericProtocol) {
                        PcControlCommand.SwitchSessionStop(sessionId, nextSequence)
                    } else {
                        PcControlCommand.LegacyGridSwitchSync(sessionId, nextSequence, emptySet())
                    }
                } ?: run {
                    legacyHeld = legacyHeldSwitchIds.toList().sorted()
                }
                true
            }
        }
        if (!shouldStop) {
            if (releaseWhenInactive && policy.releaseConnection) {
                releaseConnection(expectedConnectionEpisodeId)
            }
            return@withLock
        }
        synchronized(stateLock) {
            inactivityTimeoutJob?.cancel()
            inactivityTimeoutJob = null
        }
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
                        host.send(PcControlCommand.LegacyGridSwitchSet(switchId, down = false))
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            synchronized(stateLock) {
                if (generation == expectedGeneration) {
                    legacyHeldSwitchIds.clear()
                    mappingByKeyCode = emptyMap()
                    switchSessionId = null
                    genericProfile = null
                    useGenericProtocol = false
                    sessionReady = false
                    awaitingReconnect = false
                    nextSequence = 0L
                    stoppingGeneration = null
                    _state.value = PcSwitchControlState()
                }
            }
            val shouldRestoreScanning = synchronized(stateLock) {
                !destroying && policy.restoreScanning
            }
            if (shouldRestoreScanning) {
                try {
                    host.restoreScanning()
                } finally {
                    if (policy.releaseConnection) {
                        releaseConnection(expectedConnectionEpisodeId)
                    }
                }
            } else if (policy.releaseConnection) {
                releaseConnection(expectedConnectionEpisodeId)
            }
            if (exitReason == PcSwitchControlExitReason.InactivityTimeout) {
                host.showInactivityTimeout()
                _terminalExitReason.value = exitReason
            }
        }
    }

    private fun releaseConnection(expectedEpisodeId: String?) {
        val shouldRelease = synchronized(stateLock) {
            if (expectedEpisodeId == null || connectionEpisodeId != expectedEpisodeId) {
                false
            } else {
                connectionEpisodeId = null
                true
            }
        }
        if (shouldRelease) {
            host.releaseConnection()
        }
    }

    private fun enqueueEdge(action: EdgeAction) {
        if (edgeActions.trySend(action).isFailure) {
            stopAfterQueueOverflow()
        }
    }

    private fun stopAfterQueueOverflow() {
        val stopTarget = synchronized(stateLock) {
            if (!_state.value.active || stoppingGeneration == generation) return
            stoppingGeneration = generation
            activePresses.clear()
            _state.value = _state.value.copy(active = false)
            generation to connectionEpisodeId
        }
        snapshotJob?.cancel()
        snapshotJob = null
        forwardingScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val completion = CompletableDeferred<Unit>()
            edgeActions.send(
                EdgeAction.Stop(
                    generation = stopTarget.first,
                    connectionEpisodeId = stopTarget.second,
                    completion = completion
                )
            )
            completion.await()
        }
    }

    private suspend fun handleConnectionState(connectionState: PcServiceConnectionState) {
        var shouldSynchronize = false
        var profileToRestart: PcSwitchProfileSummary? = null
        var restartedSessionId: String? = null
        var restartedGeneration: Long? = null
        var stopConnectionEpisodeId: String? = null
        val currentGeneration = synchronized(stateLock) {
            if (!_state.value.active) return
            when (connectionState) {
                is PcServiceConnectionState.Connected -> {
                    if (useGenericProtocol && awaitingReconnect) {
                        restartedSessionId = UUID.randomUUID().toString()
                        switchSessionId = restartedSessionId
                        nextSequence = 0L
                        sessionReady = false
                        awaitingReconnect = false
                        profileToRestart = genericProfile
                        restartedGeneration = generation
                        _state.value = _state.value.copy(
                            connectionStatus = PcSwitchControlConnectionStatus.Reconnecting,
                            pcName = connectionState.displayName
                        )
                    } else {
                        sessionReady = true
                        _state.value = _state.value.copy(
                            connectionStatus = PcSwitchControlConnectionStatus.Connected,
                            pcName = connectionState.displayName
                        )
                        shouldSynchronize = switchSessionId != null
                    }
                }
                is PcServiceConnectionState.Reconnecting -> {
                    sessionReady = false
                    awaitingReconnect = true
                    _state.value = _state.value.copy(
                        connectionStatus = PcSwitchControlConnectionStatus.Reconnecting,
                        pcName = connectionState.displayName
                    )
                }
                is PcServiceConnectionState.Failed,
                PcServiceConnectionState.Disconnected -> {
                    stopConnectionEpisodeId = connectionEpisodeId
                    return@synchronized generation
                }
                else -> Unit
            }
            null
        }
        if (currentGeneration != null) {
            val completion = CompletableDeferred<Unit>()
            edgeActions.send(
                EdgeAction.Stop(
                    generation = currentGeneration,
                    connectionEpisodeId = stopConnectionEpisodeId,
                    completion = completion
                )
            )
            completion.await()
        } else if (profileToRestart != null && restartedSessionId != null) {
            val restartSucceeded = sessionLifecycleMutex.withLock {
                val restartIsCurrent = synchronized(stateLock) {
                    _state.value.active &&
                        generation == restartedGeneration &&
                        switchSessionId == restartedSessionId &&
                        genericProfile == profileToRestart &&
                        stoppingGeneration == null
                }
                if (!restartIsCurrent) return@withLock true
                val result = try {
                    host.send(
                        PcControlCommand.SwitchSessionStart(
                            sessionId = restartedSessionId,
                            profileId = profileToRestart.id,
                            profileVersion = profileToRestart.version,
                            switchCount = synchronized(stateLock) { mappingByKeyCode.size }
                        )
                    )
                } catch (error: Exception) {
                    PcCommandResult.Failed(
                        error.message ?: "PC Switch Control could not reconnect."
                    )
                }
                if (result != PcCommandResult.Ack) return@withLock false
                synchronized(stateLock) {
                    if (
                        _state.value.active &&
                        generation == restartedGeneration &&
                        switchSessionId == restartedSessionId &&
                        stoppingGeneration == null
                    ) {
                        sessionReady = true
                        _state.value = _state.value.copy(
                            connectionStatus = PcSwitchControlConnectionStatus.Connected
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
                val stopTarget = synchronized(stateLock) {
                    generation to connectionEpisodeId
                }
                edgeActions.send(
                    EdgeAction.Stop(
                        generation = stopTarget.first,
                        connectionEpisodeId = stopTarget.second,
                        completion = completion
                    )
                )
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
        const val INACTIVITY_TIMEOUT_MS = 60_000L
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
