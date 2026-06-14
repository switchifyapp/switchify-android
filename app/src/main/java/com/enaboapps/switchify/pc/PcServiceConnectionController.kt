package com.enaboapps.switchify.pc

import android.content.Context
import com.enaboapps.switchify.pc.bluetooth.PcBleDiscoveryService
import com.enaboapps.switchify.pc.bluetooth.SwitchifyPcBleClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed class PcServiceConnectionState {
    data object Disconnected : PcServiceConnectionState()
    data object Connecting : PcServiceConnectionState()
    data class Connected(val session: PcAuthenticatedSession, val displayName: String) : PcServiceConnectionState()
    data class Failed(val message: String) : PcServiceConnectionState()
}

sealed class PcServiceConnectResult {
    data class Connected(val session: PcAuthenticatedSession, val displayName: String) : PcServiceConnectResult()
    data class Failed(val reason: PcErrorReason, val message: String) : PcServiceConnectResult()
}

class PcServiceConnectionController(
    context: Context?,
    scope: CoroutineScope,
    private val discovery: PcDiscovery = PcBleDiscoveryService(requireNotNull(context).applicationContext),
    private val tokenStore: PcPairingTokenStore = PcTokenStore(requireNotNull(context).applicationContext),
    private val identityRepository: PcDeviceIdentity = PcDeviceIdentityRepository(requireNotNull(context).applicationContext),
    private val connector: PcConnector = SwitchifyPcBleClient(requireNotNull(context).applicationContext, identityRepository, tokenStore),
    private val requestNonceProvider: () -> String = { UUID.randomUUID().toString() }
) {
    private val _state = MutableStateFlow<PcServiceConnectionState>(PcServiceConnectionState.Disconnected)
    val state: StateFlow<PcServiceConnectionState> = _state

    /**
     * Discovers Switchify PCs on the local network.
     *
     * Waits up to [DISCOVERY_TIMEOUT_MS] for the first PC to resolve, then keeps a short
     * settle window open so slower PCs can join the list before it is returned. This avoids
     * deciding on a single PC just because it was the first Bluetooth result.
     */
    suspend fun discoverPcs(): List<DiscoveredPc> {
        discovery.startDiscovery()
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
            discovery.stopDiscovery()
        }
    }

    suspend fun connectOrRequestAccess(onWaitingForApproval: (PcApprovalCodeState) -> Unit = {}): PcServiceConnectResult {
        existingConnection()?.let { return it }

        _state.value = PcServiceConnectionState.Connecting
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
        existingConnection()?.takeIf { it.session.desktopId == pc.desktopId }?.let { return it }

        _state.value = PcServiceConnectionState.Connecting
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

    suspend fun sendCommand(command: PcControlCommand): PcCommandResult {
        val connected = PcConnectionStateHolder.connectionState.value as? PcConnectionState.Connected
            ?: return PcCommandResult.AuthFailed()
        val result = retryPcAuthFailure(
            block = { connector.sendCommand(connected.session, command) },
            isAuthFailure = { it is PcCommandResult.AuthFailed }
        )
        if (result is PcCommandResult.AuthFailed) {
            tokenStore.clearToken(connected.session.desktopId)
            PcConnectionStateHolder.setDisconnected()
            _state.value = PcServiceConnectionState.Failed(result.message)
        }
        return result
    }

    fun cleanup() {
        discovery.stopDiscovery()
        connector.close()
    }

    private fun existingConnection(): PcServiceConnectResult.Connected? {
        val connected = PcConnectionStateHolder.connectionState.value as? PcConnectionState.Connected ?: return null
        _state.value = PcServiceConnectionState.Connected(connected.session, connected.displayName)
        return PcServiceConnectResult.Connected(connected.session, connected.displayName)
    }

    private suspend fun pairAndConnect(
        pc: DiscoveredPc,
        onWaitingForApproval: (PcApprovalCodeState) -> Unit
    ): PcServiceConnectResult {
        val requestNonce = requestNonceProvider()
        val verificationCode = createPairingVerificationCode(
            desktopId = pc.desktopId,
            deviceId = identityRepository.getDeviceId(),
            requestNonce = requestNonce
        )
        onWaitingForApproval(PcApprovalCodeState(pc.displayName, verificationCode))
        return when (val result = connector.requestApproval(pc, requestNonce)) {
            is PcPairingResult.Paired -> {
                tokenStore.saveToken(result.desktopId, result.token, result.endpointId, pc.displayName)
                connectWithToken(pc, result.token)
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
                tokenStore.saveToken(pc.desktopId, token, result.endpointId, pc.displayName)
                val session = PcAuthenticatedSession(pc.desktopId, identityRepository.getDeviceId(), result.endpointId)
                PcConnectionStateHolder.setConnected(session, pc.displayName)
                _state.value = PcServiceConnectionState.Connected(session, pc.displayName)
                PcServiceConnectResult.Connected(session, pc.displayName)
            }
            is PcPingResult.AuthFailed -> {
                tokenStore.clearToken(pc.desktopId)
                PcConnectionStateHolder.setDisconnected()
                _state.value = PcServiceConnectionState.Failed(EXPIRED_MESSAGE)
                PcServiceConnectResult.Failed(PcErrorReason.AuthExpired, EXPIRED_MESSAGE)
            }
            is PcPingResult.Failed -> PcServiceConnectResult.Failed(result.reason, result.message)
        }
    }

    companion object {
        private const val EXPIRED_MESSAGE = "Connection expired. Request access again."
        private const val DISCOVERY_TIMEOUT_MS = 8_000L
        private const val SETTLE_WINDOW_MS = 1_500L
    }
}
