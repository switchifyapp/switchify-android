package com.enaboapps.switchify.pc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PcSwitcherRowState(
    val desktopId: String,
    val displayName: String,
    val summary: String,
    val connected: Boolean,
    val enabled: Boolean
)

internal data class PcSwitcherUiState(
    val visible: Boolean = false,
    val connectedDisplayName: String? = null,
    val rows: List<PcSwitcherRowState> = emptyList(),
    val isDiscovering: Boolean = false,
    val isPreparing: Boolean = false,
    val switchingDesktopId: String? = null,
    val approvalCode: PcApprovalCodeState? = null,
    val message: String? = null
)

internal interface PcSwitcherConnectionHost {
    val connectionState: StateFlow<PcServiceConnectionState>?
    fun currentDesktopId(): String?
    fun currentDisplayName(): String?
    suspend fun discoverPairedPcs(): List<DiscoveredPc>
    suspend fun connectTo(
        pc: DiscoveredPc,
        onWaitingForApproval: (PcApprovalCodeState) -> Unit
    ): PcServiceConnectResult
    fun cancelConnectionAttempt()
}

internal class PcServiceSwitcherConnectionHost(
    private val controllerProvider: () -> PcServiceConnectionController?
) : PcSwitcherConnectionHost {
    override val connectionState: StateFlow<PcServiceConnectionState>?
        get() = controllerProvider()?.state

    override fun currentDesktopId(): String? =
        controllerProvider()?.currentControlDesktopId()

    override fun currentDisplayName(): String? =
        controllerProvider()?.currentControlDeviceName()

    override suspend fun discoverPairedPcs(): List<DiscoveredPc> =
        controllerProvider()?.discoverPairedPcs().orEmpty()

    override suspend fun connectTo(
        pc: DiscoveredPc,
        onWaitingForApproval: (PcApprovalCodeState) -> Unit
    ): PcServiceConnectResult =
        controllerProvider()?.connectTo(pc, onWaitingForApproval)
            ?: PcServiceConnectResult.Failed(
                PcErrorReason.Failed,
                "Connect to a PC from Switchify first."
            )

    override fun cancelConnectionAttempt() {
        controllerProvider()?.cancelConnectionAttempt()
    }
}

internal class PcSwitcherCoordinator(
    private val host: PcSwitcherConnectionHost,
    private val scope: CoroutineScope,
    private val beforeOpen: suspend () -> Unit = {},
    private val beforeSwitch: suspend () -> Unit = {},
    private val afterSwitch: suspend () -> Unit = {}
) {
    private val _state = MutableStateFlow(
        PcSwitcherUiState(
            connectedDisplayName = host.currentDisplayName()
        )
    )
    val state: StateFlow<PcSwitcherUiState> = _state.asStateFlow()

    private var candidates = emptyList<DiscoveredPc>()
    private var openJob: Job? = null
    private var discoveryJob: Job? = null
    private var connectionJob: Job? = null
    private var operationGeneration = 0L

    init {
        host.connectionState?.let { connectionState ->
            scope.launch {
                connectionState.collect {
                    updateConnectedPc()
                }
            }
        }
    }

    fun open() {
        if (_state.value.visible || openJob?.isActive == true) return
        val expectedGeneration = ++operationGeneration
        openJob = scope.launch {
            _state.update { it.copy(isPreparing = true, message = null) }
            try {
                beforeOpen()
                if (expectedGeneration != operationGeneration) return@launch
                _state.update {
                    it.copy(
                        visible = true,
                        isPreparing = false,
                        approvalCode = null,
                        switchingDesktopId = null
                    )
                }
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (expectedGeneration == operationGeneration) {
                    _state.update {
                        it.copy(
                            visible = true,
                            isPreparing = false,
                            message = error.message
                        )
                    }
                }
            } finally {
                if (expectedGeneration == operationGeneration) {
                    openJob = null
                    _state.update { it.copy(isPreparing = false) }
                }
            }
        }
    }

    fun dismiss() {
        invalidateOperations(cancelConnection = true)
        _state.update {
            it.copy(
                visible = false,
                isDiscovering = false,
                isPreparing = false,
                switchingDesktopId = null,
                approvalCode = null,
                message = null
            )
        }
    }

    fun refresh() {
        discoveryJob?.cancel()
        val expectedGeneration = operationGeneration
        discoveryJob = scope.launch {
            _state.update { it.copy(isDiscovering = true, message = null) }
            try {
                val discovered = host.discoverPairedPcs()
                if (expectedGeneration != operationGeneration) return@launch
                candidates = discovered
                updateRows()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (expectedGeneration == operationGeneration) {
                    candidates = emptyList()
                    _state.update { it.copy(message = error.message) }
                    updateRows()
                }
            } finally {
                if (expectedGeneration == operationGeneration) {
                    discoveryJob = null
                    _state.update { it.copy(isDiscovering = false) }
                }
            }
        }
    }

    fun switchTo(desktopId: String) {
        val pc = candidates.firstOrNull { it.desktopId == desktopId }
        if (pc == null) {
            _state.update { it.copy(message = "No paired PC was found nearby.") }
            refresh()
            return
        }
        if (host.currentDesktopId() == desktopId) {
            dismiss()
            return
        }
        connectionJob?.cancel()
        val expectedGeneration = ++operationGeneration
        connectionJob = scope.launch {
            _state.update {
                it.copy(
                    switchingDesktopId = desktopId,
                    approvalCode = null,
                    message = null
                )
            }
            updateRows()
            try {
                beforeSwitch()
                if (expectedGeneration != operationGeneration) return@launch
                when (
                    val result = host.connectTo(pc) { approvalCode ->
                        if (expectedGeneration == operationGeneration) {
                            _state.update { it.copy(approvalCode = approvalCode) }
                        }
                    }
                ) {
                    is PcServiceConnectResult.Connected -> {
                        if (expectedGeneration != operationGeneration) return@launch
                        updateConnectedPc()
                        _state.update {
                            it.copy(
                                visible = false,
                                switchingDesktopId = null,
                                approvalCode = null,
                                message = null
                            )
                        }
                        afterSwitch()
                    }
                    is PcServiceConnectResult.Failed -> {
                        if (expectedGeneration == operationGeneration) {
                            _state.update {
                                it.copy(
                                    switchingDesktopId = null,
                                    approvalCode = null,
                                    message = result.message
                                )
                            }
                            updateRows()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (expectedGeneration == operationGeneration) {
                    _state.update {
                        it.copy(
                            switchingDesktopId = null,
                            approvalCode = null,
                            message = error.message
                        )
                    }
                    updateRows()
                }
            } finally {
                if (expectedGeneration == operationGeneration) {
                    connectionJob = null
                }
            }
        }
    }

    fun cancelPairing() {
        connectionJob?.cancel()
        connectionJob = null
        operationGeneration++
        host.cancelConnectionAttempt()
        _state.update {
            it.copy(
                switchingDesktopId = null,
                approvalCode = null,
                message = null
            )
        }
        updateRows()
    }

    fun dispose() {
        invalidateOperations(cancelConnection = true)
    }

    private fun invalidateOperations(cancelConnection: Boolean) {
        operationGeneration++
        openJob?.cancel()
        openJob = null
        discoveryJob?.cancel()
        discoveryJob = null
        val hadConnectionOperation = connectionJob != null
        connectionJob?.cancel()
        connectionJob = null
        if (cancelConnection && hadConnectionOperation) {
            host.cancelConnectionAttempt()
        }
    }

    private fun updateConnectedPc() {
        _state.update {
            it.copy(connectedDisplayName = host.currentDisplayName())
        }
        updateRows()
    }

    private fun updateRows() {
        val connectedDesktopId = host.currentDesktopId()
        val switchingDesktopId = _state.value.switchingDesktopId
        _state.update { state ->
            state.copy(
                rows = candidates.map { pc ->
                    val connected = pc.desktopId == connectedDesktopId
                    PcSwitcherRowState(
                        desktopId = pc.desktopId,
                        displayName = pc.controlDeviceName,
                        summary = pc.primaryAddress,
                        connected = connected,
                        enabled = !connected && switchingDesktopId == null
                    )
                }
            )
        }
    }
}
