package com.enaboapps.switchify.screens.pc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.pc.isSafePcTypedText
import com.enaboapps.switchify.service.core.ServiceCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PcMouseControlUiState(
    val connectedDisplayName: String? = null,
    val activeSurface: PcControlSurface = PcControlSurface.Mouse,
    val selectedMovementSize: PcMouseMovementSize = PcMouseMovementSize.Small,
    val movementStep: Int = PcMouseControlViewModel.FALLBACK_MOVEMENT_STEPS.small,
    val isBusy: Boolean = false,
    val busyCommand: PcControlCommand? = null,
    val message: String? = null,
    val typingText: String = "",
    val typingMessage: String? = null,
    val connectionStatusText: String? = null
)

class PcMouseControlViewModel(
    private val serviceControllerProvider: () -> PcServiceConnectionController?,
    private val movementSizeStore: PcMouseMovementSizeStore,
    private val controlSurfaceStore: PcControlSurfaceStore
) : ViewModel() {
    constructor(
        serviceControllerProvider: () -> PcServiceConnectionController?,
        movementSizeStore: PcMouseMovementSizeStore
    ) : this(
        serviceControllerProvider = serviceControllerProvider,
        movementSizeStore = movementSizeStore,
        controlSurfaceStore = InMemoryControlSurfaceStore()
    )

    constructor(context: Context) : this(
        serviceControllerProvider = { ServiceCore.getPcServiceConnectionController() },
        movementSizeStore = PcMouseMovementPreferenceStore(context.applicationContext),
        controlSurfaceStore = PcControlSurfacePreferenceStore(context.applicationContext)
    )

    private val _uiState = MutableStateFlow(PcMouseControlUiState())
    val uiState: StateFlow<PcMouseControlUiState> = _uiState.asStateFlow()
    private var movementSteps = FALLBACK_MOVEMENT_STEPS

    init {
        val selectedSize = movementSizeStore.getSelectedSize()
        val selectedSurface = controlSurfaceStore.getSelectedSurface()
        _uiState.update {
            it.copy(
                activeSurface = selectedSurface,
                selectedMovementSize = selectedSize,
                movementStep = movementSteps.stepFor(selectedSize)
            )
        }
        serviceControllerProvider()?.let { controller ->
            viewModelScope.launch {
                controller.state.collect { applyServiceState(it, controller) }
            }
        } ?: showConnectFirst()
        viewModelScope.launch {
            PcConnectionStateHolder.connectionState.collect { applySharedConnectionState(it) }
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

    fun send(command: PcControlCommand) {
        sendCommand(command) {
            it.copy(isBusy = false, busyCommand = null, message = null)
        }
    }

    fun selectControlSurface(surface: PcControlSurface) {
        controlSurfaceStore.setSelectedSurface(surface)
        _uiState.update {
            it.copy(
                activeSurface = surface,
                typingMessage = if (surface != PcControlSurface.Typing) null else it.typingMessage
            )
        }
    }

    fun showTypingSurface() {
        selectControlSurface(PcControlSurface.Typing)
    }

    fun showMouseSurface() {
        selectControlSurface(PcControlSurface.Mouse)
    }

    fun updateTypingText(text: String) {
        _uiState.update {
            it.copy(
                typingText = text,
                typingMessage = validationMessageFor(text)
            )
        }
    }

    fun clearTypingText() {
        _uiState.update { it.copy(typingText = "", typingMessage = null) }
    }

    fun sendTypedText() {
        val text = _uiState.value.typingText
        validationMessageFor(text)?.let { message ->
            _uiState.update { it.copy(typingMessage = message) }
            return
        }
        if (text.isEmpty()) return
        sendCommand(PcControlCommand.TypeText(text)) {
            it.copy(
                isBusy = false,
                busyCommand = null,
                typingText = "",
                typingMessage = null,
                message = null
            )
        }
    }

    fun sendKey(key: PcKeyboardKey) {
        sendCommand(PcControlCommand.PressKey(key)) {
            it.copy(
                isBusy = false,
                busyCommand = null,
                typingMessage = null,
                message = null
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onPcUiResumed() {
        serviceControllerProvider()?.onPcUiResumed()
    }

    fun onPcUiPaused() {
        serviceControllerProvider()?.onPcUiPaused()
    }

    fun stopPcBluetooth() {
        serviceControllerProvider()?.disconnect()
    }

    override fun onCleared() {
        serviceControllerProvider()?.onPcUiPaused()
        super.onCleared()
    }

    private fun sendCommand(command: PcControlCommand, onAck: (PcMouseControlUiState) -> PcMouseControlUiState) {
        val controller = serviceControllerProvider()
        val state = controller?.state?.value
        if (controller == null || !controller.hasLiveControlSession()) {
            val message = if (state is PcServiceConnectionState.Reconnecting) RECONNECTING_MESSAGE else CONNECT_FIRST_MESSAGE
            showCommandBlocked(command, message)
            return
        }
        if (state is PcServiceConnectionState.Reconnecting || state is PcServiceConnectionState.OpeningControlSession) {
            showCommandBlocked(command, RECONNECTING_MESSAGE)
            return
        }
        if (_uiState.value.isBusy) return

        _uiState.update { it.copy(isBusy = true, busyCommand = command) }
        viewModelScope.launch {
            when (val result = controller.sendControlCommand(command)) {
                PcCommandResult.Ack -> _uiState.update(onAck)
                is PcCommandResult.AuthFailed -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyCommand = null,
                            message = result.message,
                            typingMessage = if (command is PcControlCommand.TypeText || command is PcControlCommand.PressKey) {
                                result.message
                            } else {
                                it.typingMessage
                            }
                        )
                    }
                }
                is PcCommandResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyCommand = null,
                            message = if (command is PcControlCommand.TypeText || command is PcControlCommand.PressKey) {
                                it.message
                            } else {
                                result.message.ifBlank { COMMAND_FAILED_MESSAGE }
                            },
                            typingMessage = when (command) {
                                is PcControlCommand.TypeText -> TYPING_FAILED_MESSAGE
                                is PcControlCommand.PressKey -> KEY_FAILED_MESSAGE
                                else -> it.typingMessage
                            }
                        )
                    }
                }
            }
        }
    }

    private fun applyServiceState(state: PcServiceConnectionState, controller: PcServiceConnectionController) {
        when (state) {
            is PcServiceConnectionState.Connected -> {
                movementSteps = state.pointerProfile?.toMouseMovementSteps()
                    ?: controller.currentPointerProfile()?.toMouseMovementSteps()
                    ?: FALLBACK_MOVEMENT_STEPS
                _uiState.update {
                    it.copy(
                        connectedDisplayName = state.displayName,
                        movementStep = movementSteps.stepFor(it.selectedMovementSize),
                        message = if (it.message == RECONNECTING_MESSAGE || it.message == DISCONNECTED_MESSAGE || it.message == CONNECT_FIRST_MESSAGE) {
                            null
                        } else {
                            it.message
                        },
                        connectionStatusText = null
                    )
                }
            }
            is PcServiceConnectionState.Reconnecting -> {
                _uiState.update {
                    it.copy(
                        connectedDisplayName = state.displayName,
                        isBusy = false,
                        busyCommand = null,
                        message = RECONNECTING_MESSAGE,
                        connectionStatusText = RECONNECTING_MESSAGE
                    )
                }
            }
            PcServiceConnectionState.Disconnected -> {
                movementSteps = FALLBACK_MOVEMENT_STEPS
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        movementStep = movementSteps.stepFor(it.selectedMovementSize),
                        isBusy = false,
                        busyCommand = null,
                        message = CONNECT_FIRST_MESSAGE,
                        connectionStatusText = null
                    )
                }
            }
            is PcServiceConnectionState.Failed -> {
                movementSteps = FALLBACK_MOVEMENT_STEPS
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        movementStep = movementSteps.stepFor(it.selectedMovementSize),
                        isBusy = false,
                        busyCommand = null,
                        message = state.message,
                        connectionStatusText = state.message
                    )
                }
            }
            PcServiceConnectionState.Discovering,
            PcServiceConnectionState.Pairing,
            PcServiceConnectionState.OpeningControlSession -> Unit
        }
    }

    private fun applySharedConnectionState(state: PcConnectionState) {
        when (state) {
            is PcConnectionState.Reconnecting -> {
                _uiState.update {
                    it.copy(
                        connectedDisplayName = state.displayName,
                        isBusy = false,
                        busyCommand = null,
                        message = RECONNECTING_MESSAGE,
                        connectionStatusText = RECONNECTING_MESSAGE
                    )
                }
            }
            is PcConnectionState.Failed -> {
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        isBusy = false,
                        busyCommand = null,
                        message = state.message,
                        connectionStatusText = state.message
                    )
                }
            }
            PcConnectionState.Disconnected,
            is PcConnectionState.Connected -> Unit
        }
    }

    private fun showCommandBlocked(command: PcControlCommand, message: String) {
        _uiState.update {
            it.copy(
                message = message,
                typingMessage = if (command is PcControlCommand.TypeText || command is PcControlCommand.PressKey) {
                    message
                } else {
                    it.typingMessage
                }
            )
        }
    }

    private fun showConnectFirst() {
        movementSteps = FALLBACK_MOVEMENT_STEPS
        _uiState.update {
            it.copy(
                connectedDisplayName = null,
                movementStep = movementSteps.stepFor(it.selectedMovementSize),
                isBusy = false,
                busyCommand = null,
                message = CONNECT_FIRST_MESSAGE,
                connectionStatusText = null
            )
        }
    }

    companion object {
        val FALLBACK_MOVEMENT_STEPS = PcMouseMovementSteps(
            small = 40,
            medium = 80,
            large = 160
        )
        const val CONNECT_FIRST_MESSAGE = "Connect to PC from Switchify first."
        const val COMMAND_FAILED_MESSAGE = "Could not send command to PC."
        const val TYPING_FAILED_MESSAGE = "Could not send text to PC."
        const val KEY_FAILED_MESSAGE = "Could not send key to PC."
        const val TEXT_TOO_LONG_MESSAGE = "Text is too long."
        const val TEXT_UNSUPPORTED_MESSAGE = "Text includes unsupported characters."
        const val RECONNECTING_MESSAGE = "Reconnecting..."
        const val BLUETOOTH_OFF_MESSAGE = "Bluetooth is off."
        const val BLUETOOTH_PERMISSION_DENIED_MESSAGE = "Bluetooth permission denied."
        const val DISCONNECTED_MESSAGE = "Disconnected."
    }

    private fun validationMessageFor(text: String): String? {
        return when {
            text.length > PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH -> TEXT_TOO_LONG_MESSAGE
            !isSafePcTypedText(text) -> TEXT_UNSUPPORTED_MESSAGE
            else -> null
        }
    }
}

private class InMemoryControlSurfaceStore : PcControlSurfaceStore {
    private var surface = PcControlSurface.Mouse

    override fun getSelectedSurface(): PcControlSurface = surface

    override fun setSelectedSurface(surface: PcControlSurface) {
        this.surface = surface
    }
}
