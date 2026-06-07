package com.enaboapps.switchify.screens.pc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcLiveControlResult
import com.enaboapps.switchify.pc.PcMouseControlConnection
import com.enaboapps.switchify.pc.PcDeviceIdentityRepository
import com.enaboapps.switchify.pc.PcMouseCommand
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcPairingTokenStore
import com.enaboapps.switchify.pc.PcTokenStore
import com.enaboapps.switchify.pc.SwitchifyPcClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PcMouseControlUiState(
    val connectedDisplayName: String? = null,
    val selectedMovementSize: PcMouseMovementSize = PcMouseMovementSize.Small,
    val movementStep: Int = PcMouseControlViewModel.FALLBACK_MOVEMENT_STEPS.small,
    val isBusy: Boolean = false,
    val busyCommand: PcMouseCommand? = null,
    val message: String? = null
)

class PcMouseControlViewModel(
    private val tokenStore: PcPairingTokenStore,
    private val connector: PcConnector,
    private val movementSizeStore: PcMouseMovementSizeStore
) : ViewModel() {
    constructor(context: Context) : this(
        tokenStore = PcTokenStore(context.applicationContext),
        connector = SwitchifyPcClient(
            PcDeviceIdentityRepository(context.applicationContext),
            PcTokenStore(context.applicationContext)
        ),
        movementSizeStore = PcMouseMovementPreferenceStore(context.applicationContext)
    )

    private val _uiState = MutableStateFlow(PcMouseControlUiState())
    val uiState: StateFlow<PcMouseControlUiState> = _uiState.asStateFlow()
    private var liveConnection: PcMouseControlConnection? = null
    private var liveSession: PcAuthenticatedSession? = null
    private var liveConnectionDeferred: Deferred<PcMouseControlConnection?>? = null
    private var movementSteps = FALLBACK_MOVEMENT_STEPS

    init {
        val selectedSize = movementSizeStore.getSelectedSize()
        _uiState.update {
            it.copy(
                selectedMovementSize = selectedSize,
                movementStep = movementSteps.stepFor(selectedSize)
            )
        }
        viewModelScope.launch {
            PcConnectionStateHolder.connectionState.collect { state ->
                _uiState.update {
                    it.copy(
                        connectedDisplayName = (state as? PcConnectionState.Connected)?.displayName,
                        movementStep = if (state is PcConnectionState.Connected) {
                            it.movementStep
                        } else {
                            fallbackStepFor(it.selectedMovementSize)
                        }
                    )
                }
                when (state) {
                    is PcConnectionState.Connected -> ensureLiveConnection(state.session)
                    PcConnectionState.Disconnected -> closeLiveConnection()
                }
            }
        }
    }

    fun selectMovementSize(size: PcMouseMovementSize) {
        movementSizeStore.setSelectedSize(size)
        _uiState.update {
            it.copy(
                selectedMovementSize = size,
                movementStep = movementSteps.stepFor(size)
            )
        }
    }

    fun send(command: PcMouseCommand) {
        val connected = PcConnectionStateHolder.connectionState.value as? PcConnectionState.Connected
        if (connected == null) {
            _uiState.update { it.copy(message = CONNECT_FIRST_MESSAGE) }
            return
        }
        if (_uiState.value.isBusy) return

        _uiState.update { it.copy(isBusy = true, busyCommand = command) }
        viewModelScope.launch {
            val connection = liveConnection ?: ensureLiveConnection(connected.session).await()
            val result = connection?.sendMouseCommand(command) ?: PcCommandResult.Failed()
            when (result) {
                PcCommandResult.Ack -> {
                    _uiState.update { it.copy(isBusy = false, busyCommand = null, message = null) }
                }
                is PcCommandResult.AuthFailed -> {
                    tokenStore.clearToken(connected.session.desktopId)
                    PcConnectionStateHolder.setDisconnected()
                    _uiState.update {
                        it.copy(
                            connectedDisplayName = null,
                            isBusy = false,
                            busyCommand = null,
                            message = CONNECT_FIRST_MESSAGE
                        )
                    }
                }
                is PcCommandResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyCommand = null,
                            message = COMMAND_FAILED_MESSAGE
                        )
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        closeLiveConnection()
        connector.close()
        super.onCleared()
    }

    private fun ensureLiveConnection(session: PcAuthenticatedSession): Deferred<PcMouseControlConnection?> {
        liveConnection?.takeIf { liveSession == session }?.let {
            return CompletableDeferred(it)
        }
        liveConnectionDeferred?.takeIf { liveSession == session && it.isActive }?.let {
            return it
        }

        closeLiveConnection()
        liveSession = session
        return viewModelScope.async {
            when (val result = connector.openMouseControlSession(session)) {
                is PcLiveControlResult.Connected -> {
                    liveConnection = result.connection
                    movementSteps = result.connection.pointerProfile?.toMouseMovementSteps() ?: FALLBACK_MOVEMENT_STEPS
                    _uiState.update {
                        it.copy(movementStep = movementSteps.stepFor(it.selectedMovementSize))
                    }
                    result.connection
                }
                is PcLiveControlResult.AuthFailed -> {
                    tokenStore.clearToken(session.desktopId)
                    PcConnectionStateHolder.setDisconnected()
                    movementSteps = FALLBACK_MOVEMENT_STEPS
                    _uiState.update {
                        it.copy(
                            connectedDisplayName = null,
                            movementStep = movementSteps.stepFor(it.selectedMovementSize),
                            isBusy = false,
                            busyCommand = null,
                            message = CONNECT_FIRST_MESSAGE
                        )
                    }
                    null
                }
                is PcLiveControlResult.Failed -> {
                    _uiState.update {
                        it.copy(message = COMMAND_FAILED_MESSAGE)
                    }
                    null
                }
            }
        }.also {
            liveConnectionDeferred = it
        }
    }

    private fun closeLiveConnection() {
        liveConnection?.close()
        liveConnection = null
        liveSession = null
        liveConnectionDeferred?.cancel()
        liveConnectionDeferred = null
    }

    private fun fallbackStepFor(size: PcMouseMovementSize): Int {
        movementSteps = FALLBACK_MOVEMENT_STEPS
        return movementSteps.stepFor(size)
    }

    companion object {
        val FALLBACK_MOVEMENT_STEPS = PcMouseMovementSteps(
            small = 40,
            medium = 80,
            large = 160
        )
        const val CONNECT_FIRST_MESSAGE = "Connect to PC from Switchify first."
        const val COMMAND_FAILED_MESSAGE = "Could not send command to PC."
    }
}
