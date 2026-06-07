package com.enaboapps.switchify.screens.pc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcDeviceIdentityRepository
import com.enaboapps.switchify.pc.PcMouseCommand
import com.enaboapps.switchify.pc.PcPairingTokenStore
import com.enaboapps.switchify.pc.PcTokenStore
import com.enaboapps.switchify.pc.SwitchifyPcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PcMouseControlUiState(
    val connectedDisplayName: String? = null,
    val isBusy: Boolean = false,
    val busyCommand: PcMouseCommand? = null,
    val message: String? = null
)

class PcMouseControlViewModel(
    private val tokenStore: PcPairingTokenStore,
    private val connector: PcConnector
) : ViewModel() {
    constructor(context: Context) : this(
        tokenStore = PcTokenStore(context.applicationContext),
        connector = SwitchifyPcClient(
            PcDeviceIdentityRepository(context.applicationContext),
            PcTokenStore(context.applicationContext)
        )
    )

    private val _uiState = MutableStateFlow(PcMouseControlUiState())
    val uiState: StateFlow<PcMouseControlUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            PcConnectionStateHolder.connectionState.collect { state ->
                _uiState.update {
                    it.copy(connectedDisplayName = (state as? PcConnectionState.Connected)?.displayName)
                }
            }
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
            val result = connector.sendMouseCommand(connected.session, command)
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
        connector.close()
        super.onCleared()
    }

    companion object {
        const val CONNECT_FIRST_MESSAGE = "Connect to PC from Switchify first."
        const val COMMAND_FAILED_MESSAGE = "Could not send command to PC."
    }
}
