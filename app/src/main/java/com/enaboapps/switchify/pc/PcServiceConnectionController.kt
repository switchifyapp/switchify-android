package com.enaboapps.switchify.pc

import android.content.Context
import com.enaboapps.switchify.pc.bluetooth.PcBleDiscoveryService
import com.enaboapps.switchify.pc.bluetooth.SwitchifyPcBleClient
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.coroutineContext

sealed class PcServiceConnectionState {
    data object Disconnected : PcServiceConnectionState()
    data object Discovering : PcServiceConnectionState()
    data object Pairing : PcServiceConnectionState()
    data object OpeningControlSession : PcServiceConnectionState()
    data class Reconnecting(val session: PcAuthenticatedSession, val displayName: String) : PcServiceConnectionState()
    data class Connected(
        val session: PcAuthenticatedSession,
        val displayName: String,
        val pointerProfile: PcPointerMovementProfile?
    ) : PcServiceConnectionState()
    data class Failed(val message: String) : PcServiceConnectionState()
}

sealed class PcServiceConnectResult {
    data class Connected(val session: PcAuthenticatedSession, val displayName: String) : PcServiceConnectResult()
    data class Failed(val reason: PcErrorReason, val message: String) : PcServiceConnectResult()
}

class PcServiceConnectionController(
    context: Context?,
    private val scope: CoroutineScope,
    private val discovery: PcDiscovery = PcBleDiscoveryService(requireNotNull(context).applicationContext),
    private val tokenStore: PcPairingTokenStore = PcTokenStore(requireNotNull(context).applicationContext),
    private val identityRepository: PcDeviceIdentity = PcDeviceIdentityRepository(requireNotNull(context).applicationContext),
    private val connector: PcConnector = SwitchifyPcBleClient(requireNotNull(context).applicationContext, identityRepository, tokenStore),
    private val requestNonceProvider: () -> String = { UUID.randomUUID().toString() }
) {
    private val _state = MutableStateFlow<PcServiceConnectionState>(PcServiceConnectionState.Disconnected)
    val state: StateFlow<PcServiceConnectionState> = _state
    val discoveredPcs: StateFlow<List<DiscoveredPc>> get() = discovery.pcs
    val discoveryStatus: StateFlow<PcDiscoveryStatus> get() = discovery.status
    private val discoveryLease = PcDiscoveryLease(discovery)
    private val connectionAttempts = PcConnectionAttemptCoordinator()
    private val liveJobs = PcLiveConnectionJobs()
    private var liveConnection: PcControlConnection? = null
    private var liveSession: PcAuthenticatedSession? = null
    private var liveDisplayName: String? = null
    private var liveControlDeviceName: String? = null
    private var pcUiActive = false
    private var pointerProfile: PcPointerMovementProfile? = null

    fun startContinuousDiscovery() {
        acquireDiscovery()
    }

    fun stopContinuousDiscovery() {
        releaseDiscovery()
    }

    private fun acquireDiscovery() {
        discoveryLease.acquire()
    }

    private fun releaseDiscovery() {
        discoveryLease.release()
    }

    private fun hasDiscoveryRequests(): Boolean {
        return discoveryLease.isActive
    }

    suspend fun discoverPcs(): List<DiscoveredPc> {
        _state.value = PcServiceConnectionState.Discovering
        acquireDiscovery()
        try {
            var current = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
                discovery.pcs.first { pcs -> pcs.isNotEmpty() }
            } ?: return emptyList()
            while (true) {
                val grown = withTimeoutOrNull(SETTLE_WINDOW_MS) {
                    discovery.pcs.first { pcs -> pcs.size > current.size }
                } ?: break
                current = grown
            }
            return current
        } finally {
            releaseDiscovery()
        }
    }

    suspend fun discoverPairedPcs(): List<DiscoveredPc> {
        return discoverPcs().filter { pc ->
            !tokenStore.getToken(pc.desktopId).isNullOrBlank()
        }
    }

    suspend fun connectOrRequestAccess(onWaitingForApproval: (PcApprovalCodeState) -> Unit = {}): PcServiceConnectResult {
        cancelReconnect()
        return runExclusiveAttempt {
            connectOrRequestAccessInternal(onWaitingForApproval)
        }.also { logConnectFailure(it, desktopId = null) }
    }

    private suspend fun connectOrRequestAccessInternal(onWaitingForApproval: (PcApprovalCodeState) -> Unit): PcServiceConnectResult {
        existingConnection()?.let { return it }

        val discovered = discoverPcs()

        for (pc in discovered) {
            tokenStore.getToken(pc.desktopId)?.let { token ->
                when (val result = connectWithToken(pc, token)) {
                    is PcServiceConnectResult.Connected -> return result
                    is PcServiceConnectResult.Failed -> if (result.reason == PcErrorReason.AuthExpired) return result
                }
            }
        }

        var lastFailure: PcServiceConnectResult.Failed? = null
        for (pc in discovered) {
            when (val result = pairAndConnect(pc, onWaitingForApproval)) {
                is PcServiceConnectResult.Connected -> return result
                is PcServiceConnectResult.Failed -> {
                    if (isUserDecision(result.reason)) {
                        _state.value = PcServiceConnectionState.Failed(result.message)
                        return result
                    }
                    lastFailure = result
                }
            }
        }

        val failure = lastFailure ?: if (discovered.isEmpty()) {
            PcServiceConnectResult.Failed(PcErrorReason.NoPcFound, "No Switchify PC found.")
        } else {
            PcServiceConnectResult.Failed(PcErrorReason.Failed, "Could not connect to PC.")
        }
        _state.value = PcServiceConnectionState.Failed(failure.message)
        return failure
    }

    /**
     * Connects to a specific, user-selected PC. Tries a saved token first; if the token is
     * missing or expired, falls through to a fresh pairing request against that PC.
     */
    suspend fun connectTo(
        pc: DiscoveredPc,
        onWaitingForApproval: (PcApprovalCodeState) -> Unit = {}
    ): PcServiceConnectResult {
        cancelReconnect()
        return runExclusiveAttempt {
            connectToInternal(pc, onWaitingForApproval)
        }.also { logConnectFailure(it, pc.desktopId) }
    }

    private suspend fun connectToInternal(
        pc: DiscoveredPc,
        onWaitingForApproval: (PcApprovalCodeState) -> Unit
    ): PcServiceConnectResult {
        existingConnection()?.takeIf { it.session.desktopId == pc.desktopId }?.let {
            return it
        }

        tokenStore.getToken(pc.desktopId)?.let { token ->
            when (val result = connectWithToken(pc, token)) {
                is PcServiceConnectResult.Connected -> return result
                is PcServiceConnectResult.Failed -> if (result.reason != PcErrorReason.AuthExpired) {
                    _state.value = PcServiceConnectionState.Failed(result.message)
                    return result
                }
            }
        }

        val result = pairAndConnect(pc, onWaitingForApproval)
        if (result is PcServiceConnectResult.Failed) {
            _state.value = PcServiceConnectionState.Failed(result.message)
        }
        return result
    }

    suspend fun sendControlCommand(command: PcControlCommand): PcCommandResult {
        val connection = liveConnection ?: return PcCommandResult.Failed(CONNECT_FIRST_MESSAGE)
        val session = liveSession ?: return PcCommandResult.Failed(CONNECT_FIRST_MESSAGE)
        val result = retryPcAuthFailure(
            block = { connection.sendCommand(command) },
            isAuthFailure = { it is PcCommandResult.AuthFailed }
        )
        if (result is PcCommandResult.AuthFailed) {
            closeLiveConnection(PcControlCloseReason.AuthFailure)
            _state.value = PcServiceConnectionState.Failed(result.message)
            PcConnectionStateHolder.setDisconnected()
        } else if (result is PcCommandResult.Failed) {
            handleLiveConnectionFailed(session)
        }
        return result
    }

    suspend fun sendRealtimeControlCommand(command: PcControlCommand): PcCommandResult {
        val connection = liveConnection ?: return PcCommandResult.Failed(CONNECT_FIRST_MESSAGE)
        val session = liveSession ?: return PcCommandResult.Failed(CONNECT_FIRST_MESSAGE)
        val result = retryPcAuthFailure(
            block = { connection.sendRealtimeCommand(command) },
            isAuthFailure = { it is PcCommandResult.AuthFailed }
        )
        if (result is PcCommandResult.AuthFailed) {
            closeLiveConnection(PcControlCloseReason.AuthFailure)
            _state.value = PcServiceConnectionState.Failed(result.message)
            PcConnectionStateHolder.setDisconnected()
        } else if (result is PcCommandResult.Failed) {
            handleLiveConnectionFailed(session)
        }
        return result
    }

    suspend fun requestSwitchProfileCatalog(): PcProfileCatalogResult {
        val connection = liveConnection ?: return PcProfileCatalogResult.Failed(CONNECT_FIRST_MESSAGE)
        return when (val result = connection.requestSwitchProfileCatalog()) {
            is PcProfileCatalogResult.AuthFailed -> {
                closeLiveConnection(PcControlCloseReason.AuthFailure)
                _state.value = PcServiceConnectionState.Failed(result.message)
                PcConnectionStateHolder.setDisconnected()
                result
            }
            is PcProfileCatalogResult.Failed -> {
                liveSession?.let { handleLiveConnectionFailed(it) }
                result
            }
            is PcProfileCatalogResult.Loaded -> result
        }
    }

    suspend fun sendCommand(command: PcControlCommand): PcCommandResult = sendControlCommand(command)

    fun onPcUiResumed() {
        pcUiActive = true
        liveJobs.cancelUiPause()
        if (liveConnection != null) {
            startLiveHeartbeatIfNeeded()
        } else {
            val session = liveSession
            val displayName = liveDisplayName
            if (session != null && displayName != null && !liveJobs.isReconnectActive) {
                reconnectLiveSession(session, displayName)
            }
        }
    }

    fun onPcUiPaused() {
        pcUiActive = false
        liveJobs.scheduleUiPause(scope, PC_CONTROL_UI_PAUSE_SHUTDOWN_GRACE_MS) pause@ {
            if (hasActiveConnectionAttempt()) return@pause
            cancelReconnect()
            closeLiveConnection(PcControlCloseReason.UiPauseGraceExpired)
            connector.close()
            clearLiveState()
            PcConnectionStateHolder.setDisconnected()
            _state.value = PcServiceConnectionState.Disconnected
        }
    }

    fun cancelConnectionAttempt() {
        if (connectionAttempts.cancel()) restoreStateAfterCancelledAttempt()
    }

    private fun hasActiveConnectionAttempt(): Boolean {
        return connectionAttempts.isActive
    }

    private suspend fun runExclusiveAttempt(block: suspend () -> PcServiceConnectResult): PcServiceConnectResult {
        return connectionAttempts.runExclusive(block)
    }

    private fun restoreStateAfterCancelledAttempt() {
        val session = liveSession
        val displayName = liveDisplayName
        if (liveConnection != null && session != null && displayName != null) {
            _state.value = PcServiceConnectionState.Connected(session, displayName, pointerProfile)
        } else {
            _state.value = PcServiceConnectionState.Disconnected
        }
    }

    fun currentPointerProfile(): PcPointerMovementProfile? = pointerProfile

    fun currentControlDeviceName(): String? = liveControlDeviceName

    fun currentControlDesktopId(): String? = liveSession?.desktopId

    fun hasLiveControlSession(): Boolean = liveConnection != null && liveSession != null

    fun cleanup() {
        disconnect()
    }

    fun disconnect() {
        cancelConnectionAttempt()
        liveJobs.cancelUiPause()
        cancelReconnect()
        closeLiveConnection(PcControlCloseReason.ExplicitStop)
        if (!hasDiscoveryRequests()) {
            discovery.stopDiscovery()
        }
        connector.close()
        clearLiveState()
        PcConnectionStateHolder.setDisconnected()
        _state.value = PcServiceConnectionState.Disconnected
    }

    private fun existingConnection(): PcServiceConnectResult.Connected? {
        val session = liveSession ?: return null
        val displayName = liveDisplayName ?: return null
        if (liveConnection == null) return null
        _state.value = PcServiceConnectionState.Connected(session, displayName, pointerProfile)
        PcConnectionStateHolder.setConnected(session, displayName)
        return PcServiceConnectResult.Connected(session, displayName)
    }

    private suspend fun pairAndConnect(
        pc: DiscoveredPc,
        onWaitingForApproval: (PcApprovalCodeState) -> Unit
    ): PcServiceConnectResult {
        _state.value = PcServiceConnectionState.Pairing
        val requestNonce = requestNonceProvider()
        val verificationCode = createPairingVerificationCode(
            desktopId = pc.desktopId,
            deviceId = identityRepository.getDeviceId(),
            requestNonce = requestNonce
        )
        onWaitingForApproval(PcApprovalCodeState(pc.controlDeviceName, verificationCode))
        return when (val result = connector.requestApproval(pc, requestNonce)) {
            is PcPairingResult.Paired -> {
                tokenStore.saveToken(result.desktopId, result.token, result.endpointId, pc.controlDeviceName)
                val session = PcAuthenticatedSession(
                    desktopId = result.desktopId,
                    deviceId = identityRepository.getDeviceId(),
                    endpointId = result.endpointId,
                    platform = pc.bluetoothEndpoint?.platform
                )
                openLiveControlSession(session, pc.controlDeviceName, pc.controlDeviceName)
            }
            is PcPairingResult.Failed -> PcServiceConnectResult.Failed(result.reason, result.message)
        }
    }

    private fun isUserDecision(reason: PcErrorReason): Boolean {
        return reason == PcErrorReason.PairingRejected || reason == PcErrorReason.PairingRequestExpired
    }

    private suspend fun connectWithToken(pc: DiscoveredPc, token: String): PcServiceConnectResult {
        return when (val result = retryPcAuthFailure(
            block = { connector.authenticatedPing(pc, token) },
            isAuthFailure = { it is PcPingResult.AuthFailed }
        )) {
            is PcPingResult.Connected -> {
                tokenStore.saveToken(pc.desktopId, token, result.endpointId, pc.controlDeviceName)
                val session = PcAuthenticatedSession(
                    desktopId = pc.desktopId,
                    deviceId = identityRepository.getDeviceId(),
                    endpointId = result.endpointId,
                    platform = pc.bluetoothEndpoint?.platform
                )
                openLiveControlSession(session, pc.controlDeviceName, pc.controlDeviceName)
            }
            is PcPingResult.AuthFailed -> {
                PcConnectionStateHolder.setDisconnected()
                _state.value = PcServiceConnectionState.Failed(EXPIRED_MESSAGE)
                PcServiceConnectResult.Failed(PcErrorReason.AuthExpired, EXPIRED_MESSAGE)
            }
            is PcPingResult.Failed -> PcServiceConnectResult.Failed(result.reason, result.message)
        }
    }

    private suspend fun openLiveControlSession(
        session: PcAuthenticatedSession,
        displayName: String,
        controlDeviceName: String
    ): PcServiceConnectResult {
        if (liveSession == session && liveConnection != null) {
            liveControlDeviceName = controlDeviceName
            return PcServiceConnectResult.Connected(session, displayName)
        }
        closeLiveConnection(PcControlCloseReason.Reconnect)
        liveSession = session
        liveDisplayName = displayName
        liveControlDeviceName = controlDeviceName
        _state.value = PcServiceConnectionState.OpeningControlSession
        return when (val result = retryPcAuthFailure(
            block = { connector.openControlSession(session) },
            isAuthFailure = { it is PcLiveControlResult.AuthFailed }
        )) {
            is PcLiveControlResult.Connected -> {
                storeLiveConnection(result.connection, session, displayName, controlDeviceName)
                PcServiceConnectResult.Connected(session, displayName)
            }
            is PcLiveControlResult.AuthFailed -> {
                closeLiveConnection(PcControlCloseReason.AuthFailure)
                clearLiveState()
                PcConnectionStateHolder.setDisconnected()
                _state.value = PcServiceConnectionState.Failed(result.message)
                PcServiceConnectResult.Failed(PcErrorReason.AuthExpired, EXPIRED_MESSAGE)
            }
            is PcLiveControlResult.Failed -> {
                closeLiveConnection(PcControlCloseReason.CommandFailureRecovery)
                _state.value = PcServiceConnectionState.Failed(result.message)
                PcServiceConnectResult.Failed(PcErrorReason.Failed, result.message)
            }
        }
    }

    private fun storeLiveConnection(
        connection: PcControlConnection,
        session: PcAuthenticatedSession,
        displayName: String,
        controlDeviceName: String
    ) {
        liveConnection = connection
        liveSession = session
        liveDisplayName = displayName
        liveControlDeviceName = controlDeviceName
        pointerProfile = connection.pointerProfile
        tokenStore.recordSuccessfulConnection(session.desktopId)
        observeLiveConnection(connection, session)
        startLiveHeartbeatIfNeeded()
        _state.value = PcServiceConnectionState.Connected(session, displayName, pointerProfile)
        PcConnectionStateHolder.setConnected(session, displayName)
    }

    private fun observeLiveConnection(connection: PcControlConnection, session: PcAuthenticatedSession) {
        liveJobs.observe(scope) {
            connection.connectionEvents.collect {
                handleLiveConnectionFailed(session)
            }
        }
    }

    private fun startLiveHeartbeatIfNeeded() {
        val connection = liveConnection ?: return
        val session = liveSession ?: return
        if (!pcUiActive && !liveJobs.isUiPausePending) return
        liveJobs.startHeartbeat(scope) heartbeat@ {
            while (coroutineContext.isActive && (pcUiActive || liveJobs.isUiPausePending)) {
                delay(LIVE_HEARTBEAT_INTERVAL_MS)
                when (val result = connection.checkHealth()) {
                    PcCommandResult.Ack -> Unit
                    is PcCommandResult.AuthFailed -> {
                        closeLiveConnection(PcControlCloseReason.AuthFailure)
                        clearLiveState()
                        PcConnectionStateHolder.setDisconnected()
                        _state.value = PcServiceConnectionState.Failed(result.message)
                        return@heartbeat
                    }
                    is PcCommandResult.Failed -> {
                        handleLiveConnectionFailed(session)
                        return@heartbeat
                    }
                }
            }
        }
    }

    private fun handleLiveConnectionFailed(session: PcAuthenticatedSession) {
        Logger.log(
            LogEvent.PcConnectionLost,
            data = mapOf("desktopId" to session.desktopId)
        )
        val displayName = liveDisplayName ?: "Switchify PC"
        closeLiveConnection(PcControlCloseReason.UnexpectedDisconnect)
        liveSession = session
        liveDisplayName = displayName
        if (pcUiActive || liveJobs.isUiPausePending) {
            reconnectLiveSession(session, displayName)
        }
    }

    private fun reconnectLiveSession(session: PcAuthenticatedSession, displayName: String) {
        liveJobs.startReconnect(scope) reconnect@ {
            _state.value = PcServiceConnectionState.Reconnecting(session, displayName)
            PcConnectionStateHolder.setReconnecting(session, displayName)
            var failureCount = 0
            while (coroutineContext.isActive && shouldMaintainLiveConnection()) {
                closeLiveConnection(PcControlCloseReason.Reconnect)
                when (val result = connector.openControlSession(session)) {
                    is PcLiveControlResult.Connected -> {
                        storeLiveConnection(result.connection, session, displayName, liveControlDeviceName ?: displayName)
                        return@reconnect
                    }
                    is PcLiveControlResult.AuthFailed -> {
                        closeLiveConnection(PcControlCloseReason.AuthFailure)
                        clearLiveState()
                        PcConnectionStateHolder.setDisconnected()
                        _state.value = PcServiceConnectionState.Failed(result.message)
                        return@reconnect
                    }
                    is PcLiveControlResult.Failed -> {
                        if (result.reason != PcLiveControlFailureReason.Transient) {
                            closeLiveConnection(PcControlCloseReason.CommandFailureRecovery)
                            clearLiveState()
                            PcConnectionStateHolder.setFailed(result.message)
                            _state.value = PcServiceConnectionState.Failed(result.message)
                            return@reconnect
                        }
                    }
                }
                val backoffMs = PcReconnectPolicy.delayForFailure(failureCount)
                failureCount++
                delay(backoffMs)
            }
        }
    }

    private fun shouldMaintainLiveConnection(): Boolean {
        return pcUiActive || liveJobs.isUiPausePending
    }

    private fun cancelReconnect() {
        liveJobs.cancelReconnect()
    }

    private fun closeLiveConnection(reason: PcControlCloseReason) {
        liveJobs.closeConnection(liveConnection, reason)
        liveConnection = null
    }

    private fun clearLiveState() {
        liveSession = null
        liveDisplayName = null
        liveControlDeviceName = null
        pointerProfile = null
    }

    private fun logConnectFailure(result: PcServiceConnectResult, desktopId: String?) {
        if (result !is PcServiceConnectResult.Failed) return
        Logger.log(
            LogEvent.PcConnectFailed,
            data = mapOf(
                "reason" to result.reason.name,
                "message" to result.message,
                "desktopId" to desktopId
            )
        )
    }

    companion object {
        @Volatile
        private var instance: PcServiceConnectionController? = null

        fun getInstance(context: Context): PcServiceConnectionController {
            return instance ?: synchronized(this) {
                instance ?: PcServiceConnectionController(
                    context = context.applicationContext,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                ).also { instance = it }
            }
        }

        private const val EXPIRED_MESSAGE = "Connection expired. Request access again."
        private const val CONNECT_FIRST_MESSAGE = "Connect to PC from Switchify first."
        private const val LIVE_HEARTBEAT_INTERVAL_MS = 5_000L
        private const val PC_CONTROL_UI_PAUSE_SHUTDOWN_GRACE_MS = 8_000L
        private const val DISCOVERY_TIMEOUT_MS = 8_000L
        private const val SETTLE_WINDOW_MS = 1_500L
    }
}
