package com.enaboapps.switchify.pc

import android.content.Context
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
    private val discovery: PcDiscovery = PcDiscoveryService(requireNotNull(context).applicationContext),
    private val tokenStore: PcPairingTokenStore = PcTokenStore(requireNotNull(context).applicationContext),
    private val identityRepository: PcDeviceIdentity = PcDeviceIdentityRepository(requireNotNull(context).applicationContext),
    private val connector: PcConnector = SwitchifyPcClient(identityRepository, tokenStore),
    private val requestNonceProvider: () -> String = { UUID.randomUUID().toString() }
) {
    private val _state = MutableStateFlow<PcServiceConnectionState>(PcServiceConnectionState.Disconnected)
    val state: StateFlow<PcServiceConnectionState> = _state

    suspend fun connectOrRequestAccess(onWaitingForApproval: (PcApprovalCodeState) -> Unit = {}): PcServiceConnectResult {
        (PcConnectionStateHolder.connectionState.value as? PcConnectionState.Connected)?.let {
            _state.value = PcServiceConnectionState.Connected(it.session, it.displayName)
            return PcServiceConnectResult.Connected(it.session, it.displayName)
        }

        _state.value = PcServiceConnectionState.Connecting
        discovery.startDiscovery()
        val discovered = withTimeoutOrNull(8_000) {
            discovery.pcs.first { pcs -> pcs.isNotEmpty() }
        }.orEmpty()
        discovery.stopDiscovery()

        for (pc in discovered) {
            tokenStore.getToken(pc.desktopId)?.let { token ->
                when (val result = connectWithToken(pc, token)) {
                    is PcServiceConnectResult.Connected -> return result
                    is PcServiceConnectResult.Failed -> if (result.reason == PcErrorReason.AuthExpired) return result
                }
            }
        }

        for (pc in discovered) {
            val requestNonce = requestNonceProvider()
            val verificationCode = createPairingVerificationCode(
                desktopId = pc.desktopId,
                deviceId = identityRepository.getDeviceId(),
                requestNonce = requestNonce
            )
            onWaitingForApproval(PcApprovalCodeState(pc.displayName, verificationCode))
            when (val result = connector.requestApproval(pc, requestNonce)) {
                is PcPairingResult.Paired -> {
                    tokenStore.saveToken(result.desktopId, result.token, result.websocketUrl, pc.displayName)
                    when (val ping = connectWithToken(pc, result.token)) {
                        is PcServiceConnectResult.Connected -> return ping
                        is PcServiceConnectResult.Failed -> return ping
                    }
                }
                is PcPairingResult.Failed -> {
                    val failure = PcServiceConnectResult.Failed(result.reason, result.message)
                    _state.value = PcServiceConnectionState.Failed(result.message)
                    return failure
                }
            }
        }

        val failure = if (discovered.isEmpty()) {
            PcServiceConnectResult.Failed(PcErrorReason.NoPcFound, "No Switchify PC found.")
        } else {
            PcServiceConnectResult.Failed(PcErrorReason.Failed, "Could not connect to PC.")
        }
        _state.value = PcServiceConnectionState.Failed(failure.message)
        return failure
    }

    suspend fun sendCommand(command: PcControlCommand): PcCommandResult {
        val connected = PcConnectionStateHolder.connectionState.value as? PcConnectionState.Connected
            ?: return PcCommandResult.AuthFailed()
        val result = connector.sendCommand(connected.session, command)
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

    private suspend fun connectWithToken(pc: DiscoveredPc, token: String): PcServiceConnectResult {
        return when (val result = connector.authenticatedPing(pc, token)) {
            is PcPingResult.Connected -> {
                tokenStore.saveToken(pc.desktopId, token, result.websocketUrl, pc.displayName)
                val session = PcAuthenticatedSession(pc.desktopId, identityRepository.getDeviceId(), result.websocketUrl)
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
    }
}
