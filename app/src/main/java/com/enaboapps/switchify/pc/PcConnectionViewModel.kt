package com.enaboapps.switchify.pc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

data class PcApprovalCodeState(
    val pcName: String,
    val verificationCode: String
)

data class PcConnectionUiState(
    val permissionRequired: Boolean = false,
    val discoveryStatusText: String = "Searching for Switchify PC...",
    val discoveredPcs: List<PcRowState> = emptyList(),
    val savedPairings: List<PcSavedPairingRowState> = emptyList(),
    val connectedDesktopId: String? = null,
    val isDiscovering: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    val approvalCode: PcApprovalCodeState? = null,
    val pendingUnpair: PcUnpairConfirmationState? = null
)

data class PcRowState(
    val pc: DiscoveredPc,
    val title: String,
    val summary: String,
    val actionText: String,
    val enabled: Boolean,
    val status: PcRowStatus,
    val canUnpair: Boolean
)

data class PcSavedPairingRowState(
    val desktopId: String,
    val title: String,
    val summary: String,
    val canUnpair: Boolean = true
)

data class PcUnpairConfirmationState(
    val desktopId: String,
    val displayName: String
)

enum class PcRowStatus {
    Idle,
    Connecting,
    WaitingApproval,
    Connected,
    Failed
}

class PcConnectionViewModel(
    context: Context? = null,
    private val discoveryService: PcDiscovery = PcDiscoveryService(requireNotNull(context).applicationContext),
    private val tokenStore: PcPairingTokenStore = PcTokenStore(requireNotNull(context).applicationContext),
    private val identityRepository: PcDeviceIdentity = PcDeviceIdentityRepository(requireNotNull(context).applicationContext),
    private val connector: PcConnector = SwitchifyPcClient(identityRepository, tokenStore),
    private val requestNonceProvider: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {
    private val _uiState = MutableStateFlow(PcConnectionUiState())
    val uiState: StateFlow<PcConnectionUiState> = _uiState.asStateFlow()

    private val rowStatuses = MutableStateFlow<Map<String, PcRowStatus>>(emptyMap())

    init {
        viewModelScope.launch {
            combine(discoveryService.pcs, discoveryService.status, rowStatuses, PcConnectionStateHolder.connectionState) { pcs, status, statuses, connection ->
                val connectedDesktopId = (connection as? PcConnectionState.Connected)?.session?.desktopId
                val discoveredDesktopIds = pcs.map { it.desktopId }.toSet()
                _uiState.value.copy(
                    discoveryStatusText = discoveryStatusText(status, pcs.isEmpty()),
                    discoveredPcs = pcs.map { pc -> rowState(pc, statuses[pc.desktopId] ?: PcRowStatus.Idle, connectedDesktopId) },
                    savedPairings = savedPairings(discoveredDesktopIds),
                    connectedDesktopId = connectedDesktopId,
                    isDiscovering = status == PcDiscoveryStatus.Searching
                )
            }.collect { _uiState.value = it }
        }
    }

    fun startDiscovery() {
        discoveryService.startDiscovery()
    }

    fun setPermissionRequired(required: Boolean) {
        _uiState.value = _uiState.value.copy(permissionRequired = required)
    }

    fun requestAccess(pc: DiscoveredPc) {
        viewModelScope.launch {
            val deviceId = identityRepository.getDeviceId()
            val requestNonce = requestNonceProvider()
            val verificationCode = createPairingVerificationCode(
                desktopId = pc.desktopId,
                deviceId = deviceId,
                requestNonce = requestNonce
            )
            setBusy(
                desktopId = pc.desktopId,
                status = PcRowStatus.WaitingApproval,
                message = null,
                approvalCode = PcApprovalCodeState(pc.displayName, verificationCode)
            )
            when (val pairing = connector.requestApproval(pc, requestNonce)) {
                is PcPairingResult.Paired -> {
                    when (val ping = connector.authenticatedPing(pc, pairing.token)) {
                        is PcPingResult.Connected -> {
                            tokenStore.saveToken(pc.desktopId, pairing.token, ping.websocketUrl, pc.displayName)
                            PcConnectionStateHolder.setConnected(
                                PcAuthenticatedSession(pc.desktopId, identityRepository.getDeviceId(), ping.websocketUrl),
                                pc.displayName
                            )
                            setIdle(pc.desktopId, PcRowStatus.Connected, null)
                        }
                        is PcPingResult.AuthFailed -> {
                            tokenStore.clearToken(pc.desktopId)
                            PcConnectionStateHolder.setDisconnected()
                            setIdle(pc.desktopId, PcRowStatus.Failed, ping.message)
                        }
                        is PcPingResult.Failed -> setIdle(pc.desktopId, PcRowStatus.Failed, ping.message)
                    }
                }
                is PcPairingResult.Failed -> setIdle(pc.desktopId, PcRowStatus.Failed, pairing.message)
            }
        }
    }

    fun connectWithSavedToken(pc: DiscoveredPc) {
        viewModelScope.launch {
            val token = tokenStore.getToken(pc.desktopId)
            if (token.isNullOrBlank()) {
                requestAccess(pc)
                return@launch
            }
            setBusy(pc.desktopId, PcRowStatus.Connecting, null)
            when (val result = connector.authenticatedPing(pc, token)) {
                is PcPingResult.Connected -> {
                    tokenStore.saveToken(pc.desktopId, token, result.websocketUrl, pc.displayName)
                    PcConnectionStateHolder.setConnected(
                        PcAuthenticatedSession(pc.desktopId, identityRepository.getDeviceId(), result.websocketUrl),
                        pc.displayName
                    )
                    setIdle(pc.desktopId, PcRowStatus.Connected, null)
                }
                is PcPingResult.AuthFailed -> {
                    tokenStore.clearToken(pc.desktopId)
                    PcConnectionStateHolder.setDisconnected()
                    setIdle(pc.desktopId, PcRowStatus.Failed, result.message)
                }
                is PcPingResult.Failed -> setIdle(pc.desktopId, PcRowStatus.Failed, result.message)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun requestUnpair(desktopId: String, displayName: String) {
        _uiState.value = _uiState.value.copy(
            pendingUnpair = PcUnpairConfirmationState(desktopId, displayName)
        )
    }

    fun dismissUnpair() {
        _uiState.value = _uiState.value.copy(pendingUnpair = null)
    }

    fun confirmUnpair() {
        val unpair = _uiState.value.pendingUnpair ?: return
        tokenStore.clearToken(unpair.desktopId)
        val connected = PcConnectionStateHolder.connectionState.value as? PcConnectionState.Connected
        if (connected?.session?.desktopId == unpair.desktopId) {
            PcConnectionStateHolder.setDisconnected()
        }
        rowStatuses.value = rowStatuses.value - unpair.desktopId
        _uiState.value = _uiState.value.copy(
            message = "Unpaired from ${unpair.displayName}.",
            pendingUnpair = null
        )
    }

    override fun onCleared() {
        discoveryService.stopDiscovery()
        connector.close()
        super.onCleared()
    }

    private fun rowState(pc: DiscoveredPc, status: PcRowStatus, connectedDesktopId: String?): PcRowState {
        val hasToken = !tokenStore.getToken(pc.desktopId).isNullOrBlank()
        val connected = connectedDesktopId == pc.desktopId || status == PcRowStatus.Connected
        val actionText = when {
            connected -> "Connected"
            hasToken -> "Connect"
            else -> "Request access"
        }
        val summary = when {
            connected -> "Connected"
            status == PcRowStatus.WaitingApproval -> "Waiting for approval on your PC..."
            status == PcRowStatus.Connecting -> "Connecting..."
            status == PcRowStatus.Failed -> "Try again"
            else -> pc.primaryAddress
        }
        return PcRowState(
            pc = pc,
            title = pc.displayName,
            summary = summary,
            actionText = actionText,
            enabled = !connected && !_uiState.value.isBusy,
            status = if (connected) PcRowStatus.Connected else status,
            canUnpair = hasToken || connected
        )
    }

    private fun savedPairings(discoveredDesktopIds: Set<String>): List<PcSavedPairingRowState> {
        return tokenStore.listPairings()
            .filterNot { it.desktopId in discoveredDesktopIds }
            .map { pairing ->
                PcSavedPairingRowState(
                    desktopId = pairing.desktopId,
                    title = pairing.serviceName ?: pairing.desktopId,
                    summary = pairing.lastUrl ?: "Not nearby"
                )
            }
    }

    private fun setBusy(
        desktopId: String,
        status: PcRowStatus,
        message: String?,
        approvalCode: PcApprovalCodeState? = null
    ) {
        rowStatuses.value = rowStatuses.value + (desktopId to status)
        _uiState.value = _uiState.value.copy(
            isBusy = true,
            message = message,
            approvalCode = approvalCode
        )
    }

    private fun setIdle(desktopId: String, status: PcRowStatus, message: String?) {
        rowStatuses.value = rowStatuses.value + (desktopId to status)
        _uiState.value = _uiState.value.copy(
            isBusy = false,
            message = message,
            approvalCode = null
        )
    }

    private fun discoveryStatusText(status: PcDiscoveryStatus, empty: Boolean): String {
        return when {
            status == PcDiscoveryStatus.Searching -> "Searching for Switchify PC..."
            status == PcDiscoveryStatus.Failed -> "Could not start PC discovery."
            empty -> "No PCs found yet."
            else -> "Nearby PCs"
        }
    }
}
