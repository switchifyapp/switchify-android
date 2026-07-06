package com.enaboapps.switchify.pc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.bluetooth.PcBleDiscoveryService
import com.enaboapps.switchify.pc.bluetooth.SwitchifyPcBleClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class PcApprovalCodeState(
    val pcName: String,
    val verificationCode: String
)

data class PcConnectionUiState(
    val permissionRequired: Boolean = false,
    val discoveryStatus: PcDiscoveryStatus = PcDiscoveryStatus.Empty,
    val discoveryStatusText: String = "Searching for Switchify PC...",
    val defaultPreference: PcDefaultPcPreference = PcDefaultPcPreference.LastConnection,
    val lastConnectedDesktopId: String? = null,
    val defaultPcChoices: List<PcDefaultPcChoice> = emptyList(),
    val pcRows: List<PcConnectionRowState> = emptyList(),
    val discoveredPcs: List<PcRowState> = emptyList(),
    val savedPairings: List<PcSavedPairingRowState> = emptyList(),
    val connectedDesktopId: String? = null,
    val isDiscovering: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    val approvalCode: PcApprovalCodeState? = null,
    val pendingUnpair: PcUnpairConfirmationState? = null
)

data class PcDefaultPcChoice(
    val preference: PcDefaultPcPreference,
    val title: String,
    val description: String
)

data class PcRowState(
    val pc: DiscoveredPc,
    val title: String,
    val summary: String,
    val actionText: String,
    val enabled: Boolean,
    val status: PcRowStatus,
    val canUnpair: Boolean,
    val canSetDefault: Boolean,
    val isDefault: Boolean
)

data class PcSavedPairingRowState(
    val desktopId: String,
    val title: String,
    val summary: String,
    val canUnpair: Boolean = true,
    val canConnect: Boolean = false,
    val canSetDefault: Boolean = false,
    val isDefault: Boolean = false
)

data class PcConnectionRowState(
    val desktopId: String,
    val title: String,
    val summary: String,
    val source: PcConnectionRowSource,
    val status: PcRowStatus,
    val actionText: String?,
    val enabled: Boolean,
    val canRequestAccess: Boolean,
    val canConnect: Boolean,
    val canUnpair: Boolean,
    val canSetDefault: Boolean,
    val isDefault: Boolean,
    val discoveredPc: DiscoveredPc?
)

enum class PcConnectionRowSource {
    Discovered,
    SavedOnly
}

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
    private val discoveryService: PcDiscovery = PcBleDiscoveryService(requireNotNull(context).applicationContext),
    private val tokenStore: PcPairingTokenStore = PcTokenStore(requireNotNull(context).applicationContext),
    private val identityRepository: PcDeviceIdentity = PcDeviceIdentityRepository(requireNotNull(context).applicationContext),
    private val connector: PcConnector = SwitchifyPcBleClient(requireNotNull(context).applicationContext, identityRepository, tokenStore),
    private val requestNonceProvider: () -> String = { UUID.randomUUID().toString() },
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val _uiState = MutableStateFlow(PcConnectionUiState())
    val uiState: StateFlow<PcConnectionUiState> = _uiState.asStateFlow()

    private val rowStatuses = MutableStateFlow<Map<String, PcRowStatus>>(emptyMap())
    private var activeConnectionJob: Job? = null
    private var activeConnectionAttemptId = 0

    // Bumped on every token store mutation so the combine pipeline re-reads
    // token-derived state (saved pairings, row actions); the store itself is not observable.
    private val tokenRevision = MutableStateFlow(0)

    private data class DiscoveryInputs(
        val pcs: List<DiscoveredPc>,
        val status: PcDiscoveryStatus,
        val statuses: Map<String, PcRowStatus>,
        val connection: PcConnectionState
    )

    init {
        viewModelScope.launch {
            combine(discoveryService.pcs, discoveryService.status, rowStatuses, PcConnectionStateHolder.connectionState, tokenRevision) { pcs, status, statuses, connection, _ ->
                DiscoveryInputs(pcs, status, statuses, connection)
            }.collect { inputs ->
                val connectedDesktopId = (inputs.connection as? PcConnectionState.Connected)?.session?.desktopId
                val discoveredDesktopIds = inputs.pcs.map { it.desktopId }.toSet()
                val hasTokenByDesktopId = withContext(backgroundDispatcher) {
                    inputs.pcs.associate { pc -> pc.desktopId to !tokenStore.getToken(pc.desktopId).isNullOrBlank() }
                }
                val allPairings = withContext(backgroundDispatcher) {
                    tokenStore.listPairings()
                }
                val savedPairings = withContext(backgroundDispatcher) {
                    savedPairings(allPairings, discoveredDesktopIds)
                }
                val defaultPreference = withContext(backgroundDispatcher) {
                    tokenStore.getDefaultPcPreference()
                }
                val defaultDesktopId = (defaultPreference as? PcDefaultPcPreference.SpecificPc)?.desktopId
                val lastConnectedDesktopId = withContext(backgroundDispatcher) {
                    tokenStore.getLastConnectedDesktopId()
                }
                val pcRows = buildPcRows(
                    pcs = inputs.pcs,
                    statuses = inputs.statuses,
                    connectedDesktopId = connectedDesktopId,
                    hasTokenByDesktopId = hasTokenByDesktopId,
                    savedPairings = savedPairings,
                    defaultDesktopId = defaultDesktopId,
                    isBusy = _uiState.value.isBusy
                )
                _uiState.update { current ->
                    current.copy(
                        discoveryStatus = inputs.status,
                        discoveryStatusText = discoveryStatusText(inputs.status, inputs.pcs.isEmpty()),
                        defaultPreference = defaultPreference,
                        lastConnectedDesktopId = lastConnectedDesktopId,
                        defaultPcChoices = buildDefaultPcChoices(allPairings, inputs.pcs, lastConnectedDesktopId),
                        pcRows = pcRows,
                        discoveredPcs = inputs.pcs.map { pc ->
                            rowState(
                                pc = pc,
                                status = inputs.statuses[pc.desktopId] ?: PcRowStatus.Idle,
                                connectedDesktopId = connectedDesktopId,
                                hasToken = hasTokenByDesktopId[pc.desktopId] == true,
                                defaultDesktopId = defaultDesktopId,
                                isBusy = current.isBusy
                            )
                        },
                        savedPairings = savedPairings.map { row ->
                            row.copy(isDefault = row.desktopId == defaultDesktopId)
                        },
                        connectedDesktopId = connectedDesktopId,
                        isDiscovering = inputs.status == PcDiscoveryStatus.Searching
                    )
                }
            }
        }
    }

    fun startDiscovery() {
        discoveryService.startDiscovery()
    }

    fun setPermissionRequired(required: Boolean) {
        _uiState.update { it.copy(permissionRequired = required) }
    }

    fun requestAccess(pc: DiscoveredPc) {
        val attemptId = beginExclusiveConnectionAttempt()
        activeConnectionJob = viewModelScope.launch {
            try {
                requestAccessInternal(pc, attemptId)
            } finally {
                clearActiveConnectionAttempt(attemptId)
            }
        }
    }

    fun connectWithSavedToken(pc: DiscoveredPc) {
        val attemptId = beginExclusiveConnectionAttempt()
        activeConnectionJob = viewModelScope.launch {
            try {
                connectWithSavedTokenInternal(pc, attemptId)
            } finally {
                clearActiveConnectionAttempt(attemptId)
            }
        }
    }

    fun connectSavedPairing(desktopId: String) {
        val attemptId = beginExclusiveConnectionAttempt()
        activeConnectionJob = viewModelScope.launch {
            try {
                val endpoint = tokenStore.getLastEndpointId(desktopId)
                    ?: run {
                        if (isActiveConnectionAttempt(attemptId)) {
                            showMessage("This PC is not nearby.")
                        }
                        return@launch
                    }
                val displayName = tokenStore.getServiceName(desktopId) ?: "Switchify PC"
                connectWithSavedTokenInternal(
                    DiscoveredPc(
                        serviceName = displayName,
                        desktopId = desktopId,
                        bluetoothEndpoint = PcBluetoothEndpoint(
                            deviceAddress = endpoint,
                            deviceName = null,
                            desktopId = desktopId,
                            displayName = displayName
                        )
                    ),
                    attemptId
                )
            } finally {
                clearActiveConnectionAttempt(attemptId)
            }
        }
    }

    fun cancelPairing() {
        activeConnectionJob?.cancel()
        activeConnectionJob = null
        activeConnectionAttemptId++
        connector.close()
        rowStatuses.update { statuses ->
            statuses.mapValues { (_, status) ->
                if (status == PcRowStatus.WaitingApproval || status == PcRowStatus.Connecting) {
                    PcRowStatus.Idle
                } else {
                    status
                }
            }
        }
        _uiState.update {
            it.copy(
                isBusy = false,
                approvalCode = null,
                message = null
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun requestUnpair(desktopId: String, displayName: String) {
        _uiState.update {
            it.copy(pendingUnpair = PcUnpairConfirmationState(desktopId, displayName))
        }
    }

    fun dismissUnpair() {
        _uiState.update { it.copy(pendingUnpair = null) }
    }

    fun confirmUnpair() {
        val unpair = _uiState.value.pendingUnpair ?: return
        tokenStore.clearToken(unpair.desktopId)
        tokenRevision.update { it + 1 }
        val connected = PcConnectionStateHolder.connectionState.value as? PcConnectionState.Connected
        if (connected?.session?.desktopId == unpair.desktopId) {
            PcConnectionStateHolder.setDisconnected()
        }
        rowStatuses.update { it - unpair.desktopId }
        _uiState.update {
            it.copy(
                message = "Unpaired from ${unpair.displayName}.",
                pendingUnpair = null
            )
        }
    }

    fun setDefaultPc(desktopId: String, displayName: String) {
        setDefaultPcPreference(PcDefaultPcPreference.SpecificPc(desktopId), displayName)
    }

    fun clearDefaultPc() {
        setDefaultPcPreference(PcDefaultPcPreference.LastConnection)
    }

    fun setDefaultPcPreference(preference: PcDefaultPcPreference) {
        val displayName = (preference as? PcDefaultPcPreference.SpecificPc)?.desktopId?.let { desktopId ->
            _uiState.value.pcRows.firstOrNull { it.desktopId == desktopId }?.title
                ?: tokenStore.getServiceName(desktopId)
                ?: desktopId
        }
        setDefaultPcPreference(preference, displayName)
    }

    private fun setDefaultPcPreference(
        preference: PcDefaultPcPreference,
        displayName: String? = null
    ) {
        if (preference is PcDefaultPcPreference.SpecificPc && tokenStore.getToken(preference.desktopId).isNullOrBlank()) {
            return
        }
        tokenStore.setDefaultPcPreference(preference)
        tokenRevision.update { it + 1 }
        _uiState.update {
            val message = when (preference) {
                PcDefaultPcPreference.LastConnection -> "Default set to last connection."
                is PcDefaultPcPreference.SpecificPc -> "Default PC set to ${displayName ?: preference.desktopId}."
            }
            it.copy(message = message)
        }
    }

    fun stopPcBluetooth() {
        activeConnectionJob?.cancel()
        activeConnectionJob = null
        activeConnectionAttemptId++
        discoveryService.stopDiscovery()
        connector.close()
        PcConnectionStateHolder.setDisconnected()
        rowStatuses.update { emptyMap() }
        _uiState.update {
            it.copy(
                isBusy = false,
                approvalCode = null,
                isDiscovering = false
            )
        }
    }

    override fun onCleared() {
        stopPcBluetooth()
        super.onCleared()
    }

    private fun rowState(
        pc: DiscoveredPc,
        status: PcRowStatus,
        connectedDesktopId: String?,
        hasToken: Boolean,
        defaultDesktopId: String?,
        isBusy: Boolean
    ): PcRowState {
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
            title = pc.controlDeviceName,
            summary = summary,
            actionText = actionText,
            enabled = !connected && !isBusy,
            status = if (connected) PcRowStatus.Connected else status,
            canUnpair = hasToken || connected,
            canSetDefault = hasToken,
            isDefault = pc.desktopId == defaultDesktopId
        )
    }

    private fun savedPairings(
        pairings: List<PcStoredPairing>,
        discoveredDesktopIds: Set<String>
    ): List<PcSavedPairingRowState> {
        return pairings
            .filterNot { it.desktopId in discoveredDesktopIds }
            .map { pairing ->
                PcSavedPairingRowState(
                    desktopId = pairing.desktopId,
                    title = pairing.serviceName ?: pairing.desktopId,
                    summary = savedPairingSummary(pairing.lastEndpointId),
                    canConnect = canConnectSavedPairing(pairing.lastEndpointId),
                    canSetDefault = true
                )
            }
    }

    private fun buildDefaultPcChoices(
        pairings: List<PcStoredPairing>,
        discoveredPcs: List<DiscoveredPc>,
        lastConnectedDesktopId: String?
    ): List<PcDefaultPcChoice> {
        val discoveredNames = discoveredPcs.associate { it.desktopId to it.controlDeviceName }
        val lastConnectionDisplayName = pairings.firstOrNull {
            it.desktopId == lastConnectedDesktopId
        }?.let { pairing -> discoveredNames[pairing.desktopId] ?: pairing.serviceName ?: pairing.desktopId }
        val lastConnectionDescription = lastConnectionDisplayName?.let {
            "Currently: $it"
        } ?: "Switchify will use the most recently connected PC."
        return listOf(
            PcDefaultPcChoice(
                preference = PcDefaultPcPreference.LastConnection,
                title = "Use last connection",
                description = lastConnectionDescription
            )
        ) + pairings.map { pairing ->
            PcDefaultPcChoice(
                preference = PcDefaultPcPreference.SpecificPc(pairing.desktopId),
                title = discoveredNames[pairing.desktopId] ?: pairing.serviceName ?: pairing.desktopId,
                description = "Always connect to this PC when it is available."
            )
        }
    }

    private fun buildPcRows(
        pcs: List<DiscoveredPc>,
        statuses: Map<String, PcRowStatus>,
        connectedDesktopId: String?,
        hasTokenByDesktopId: Map<String, Boolean>,
        savedPairings: List<PcSavedPairingRowState>,
        defaultDesktopId: String?,
        isBusy: Boolean
    ): List<PcConnectionRowState> {
        val discoveredRows = pcs.map { pc ->
            discoveredPcRow(
                pc = pc,
                status = statuses[pc.desktopId] ?: PcRowStatus.Idle,
                connectedDesktopId = connectedDesktopId,
                hasToken = hasTokenByDesktopId[pc.desktopId] == true,
                defaultDesktopId = defaultDesktopId,
                isBusy = isBusy
            )
        }
        val savedOnlyRows = savedPairings.map { pairing ->
            savedOnlyPcRow(pairing, defaultDesktopId)
        }
        return discoveredRows + savedOnlyRows
    }

    private fun discoveredPcRow(
        pc: DiscoveredPc,
        status: PcRowStatus,
        connectedDesktopId: String?,
        hasToken: Boolean,
        defaultDesktopId: String?,
        isBusy: Boolean
    ): PcConnectionRowState {
        val connected = connectedDesktopId == pc.desktopId || status == PcRowStatus.Connected
        val rowStatus = if (connected) PcRowStatus.Connected else status
        val actionText = when {
            connected -> null
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
        return PcConnectionRowState(
            desktopId = pc.desktopId,
            title = pc.controlDeviceName,
            summary = summary,
            source = PcConnectionRowSource.Discovered,
            status = rowStatus,
            actionText = actionText,
            enabled = !connected && !isBusy,
            canRequestAccess = !hasToken && !connected,
            canConnect = hasToken && !connected,
            canUnpair = hasToken || connected,
            canSetDefault = hasToken,
            isDefault = pc.desktopId == defaultDesktopId,
            discoveredPc = pc
        )
    }

    private fun savedOnlyPcRow(
        pairing: PcSavedPairingRowState,
        defaultDesktopId: String?
    ): PcConnectionRowState {
        return PcConnectionRowState(
            desktopId = pairing.desktopId,
            title = pairing.title,
            summary = if (pairing.canConnect) pairing.summary else "Not available",
            source = PcConnectionRowSource.SavedOnly,
            status = PcRowStatus.Idle,
            actionText = if (pairing.canConnect) "Connect" else null,
            enabled = pairing.canConnect,
            canRequestAccess = false,
            canConnect = pairing.canConnect,
            canUnpair = pairing.canUnpair,
            canSetDefault = pairing.canSetDefault,
            isDefault = pairing.desktopId == defaultDesktopId,
            discoveredPc = null
        )
    }

    private fun savedPairingSummary(lastEndpoint: String?): String {
        if (lastEndpoint.isNullOrBlank()) return "Not nearby"
        return lastEndpoint
    }

    private fun canConnectSavedPairing(lastEndpoint: String?): Boolean {
        return !lastEndpoint.isNullOrBlank()
    }

    private suspend fun requestAccessInternal(pc: DiscoveredPc, attemptId: Int) {
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
            approvalCode = PcApprovalCodeState(pc.controlDeviceName, verificationCode)
        )
        when (val pairing = connector.requestApproval(pc, requestNonce)) {
            is PcPairingResult.Paired -> {
                if (!isActiveConnectionAttempt(attemptId)) return
                when (val ping = retryPcAuthFailure(
                    block = { connector.authenticatedPing(pc, pairing.token) },
                    isAuthFailure = { it is PcPingResult.AuthFailed }
                )) {
                    is PcPingResult.Connected -> {
                        if (!isActiveConnectionAttempt(attemptId)) return
                        tokenStore.saveToken(pc.desktopId, pairing.token, ping.endpointId, pc.controlDeviceName)
                        tokenStore.recordSuccessfulConnection(pc.desktopId)
                        tokenRevision.update { it + 1 }
                        PcConnectionStateHolder.setConnected(
                            PcAuthenticatedSession(pc.desktopId, identityRepository.getDeviceId(), ping.endpointId),
                            pc.controlDeviceName
                        )
                        setIdle(pc.desktopId, PcRowStatus.Connected, null)
                    }
                    is PcPingResult.AuthFailed -> {
                        if (!isActiveConnectionAttempt(attemptId)) return
                        PcConnectionStateHolder.setDisconnected()
                        setIdle(pc.desktopId, PcRowStatus.Failed, ping.message)
                    }
                    is PcPingResult.Failed -> if (isActiveConnectionAttempt(attemptId)) {
                        setIdle(pc.desktopId, PcRowStatus.Failed, ping.message)
                    }
                }
            }
            is PcPairingResult.Failed -> if (isActiveConnectionAttempt(attemptId)) {
                setIdle(pc.desktopId, PcRowStatus.Failed, pairing.message)
            }
        }
    }

    private suspend fun connectWithSavedTokenInternal(pc: DiscoveredPc, attemptId: Int) {
        val token = tokenStore.getToken(pc.desktopId)
        if (token.isNullOrBlank()) {
            requestAccessInternal(pc, attemptId)
            return
        }
        setBusy(pc.desktopId, PcRowStatus.Connecting, null)
        when (val result = retryPcAuthFailure(
            block = { connector.authenticatedPing(pc, token) },
            isAuthFailure = { it is PcPingResult.AuthFailed }
        )) {
            is PcPingResult.Connected -> {
                if (!isActiveConnectionAttempt(attemptId)) return
                tokenStore.saveToken(pc.desktopId, token, result.endpointId, pc.controlDeviceName)
                tokenStore.recordSuccessfulConnection(pc.desktopId)
                tokenRevision.update { it + 1 }
                PcConnectionStateHolder.setConnected(
                    PcAuthenticatedSession(pc.desktopId, identityRepository.getDeviceId(), result.endpointId),
                    pc.controlDeviceName
                )
                setIdle(pc.desktopId, PcRowStatus.Connected, null)
            }
            is PcPingResult.AuthFailed -> {
                if (!isActiveConnectionAttempt(attemptId)) return
                PcConnectionStateHolder.setDisconnected()
                setIdle(pc.desktopId, PcRowStatus.Failed, result.message)
            }
            is PcPingResult.Failed -> if (isActiveConnectionAttempt(attemptId)) {
                setIdle(pc.desktopId, PcRowStatus.Failed, result.message)
            }
        }
    }

    private fun beginExclusiveConnectionAttempt(): Int {
        val previousJob = activeConnectionJob
        previousJob?.cancel()
        activeConnectionJob = null
        activeConnectionAttemptId++
        if (previousJob != null) {
            connector.close()
        }
        resetPendingConnectionRows()
        _uiState.update {
            it.copy(
                isBusy = false,
                approvalCode = null
            )
        }
        return activeConnectionAttemptId
    }

    private fun clearActiveConnectionAttempt(attemptId: Int) {
        if (activeConnectionAttemptId == attemptId) {
            activeConnectionJob = null
        }
    }

    private fun isActiveConnectionAttempt(attemptId: Int): Boolean {
        return activeConnectionAttemptId == attemptId
    }

    private fun resetPendingConnectionRows() {
        rowStatuses.update { statuses ->
            statuses.mapValues { (_, status) ->
                if (status == PcRowStatus.WaitingApproval || status == PcRowStatus.Connecting) {
                    PcRowStatus.Idle
                } else {
                    status
                }
            }
        }
    }

    private fun setBusy(
        desktopId: String,
        status: PcRowStatus,
        message: String?,
        approvalCode: PcApprovalCodeState? = null
    ) {
        rowStatuses.update { it + (desktopId to status) }
        _uiState.update {
            it.copy(
                isBusy = true,
                message = message,
                approvalCode = approvalCode
            )
        }
    }

    private fun setIdle(desktopId: String, status: PcRowStatus, message: String?) {
        rowStatuses.update { it + (desktopId to status) }
        _uiState.update {
            it.copy(
                isBusy = false,
                message = message,
                approvalCode = null
            )
        }
    }

    private fun discoveryStatusText(status: PcDiscoveryStatus, empty: Boolean): String {
        return when {
            status == PcDiscoveryStatus.Searching -> "Searching for Switchify PC over Bluetooth..."
            status == PcDiscoveryStatus.Failed -> "Could not start Bluetooth discovery."
            empty -> "No Bluetooth PCs found yet."
            else -> "Nearby PCs"
        }
    }

}
