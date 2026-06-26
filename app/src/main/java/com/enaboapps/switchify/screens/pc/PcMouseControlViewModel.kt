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
import com.enaboapps.switchify.pc.PcMouseRepeatManager
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.pc.PcTextStreamItem
import com.enaboapps.switchify.pc.isSafePcTypedText
import com.enaboapps.switchify.pc.pcTextStreamItemsFor
import com.enaboapps.switchify.pc.supportsTextStreams
import com.enaboapps.switchify.service.core.ServiceCore
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PcMouseControlUiState(
    val connectedDisplayName: String? = null,
    val activeSurface: PcControlSurface = PcControlSurface.Mouse,
    val selectedMovementSize: PcMouseMovementSize = PcMouseMovementSize.Small,
    val movementStep: Int = PcMouseControlViewModel.FALLBACK_MOVEMENT_STEPS.small,
    val isDragging: Boolean = false,
    val isBusy: Boolean = false,
    val busyCommand: PcControlCommand? = null,
    val message: String? = null,
    val typingText: String = "",
    val typingMessage: String? = null,
    val supportsTextStreamInput: Boolean = false,
    val connectionStatusText: String? = null
)

class PcMouseControlViewModel(
    private val serviceControllerProvider: () -> PcServiceConnectionController?,
    private val movementSizeStore: PcMouseMovementSizeStore,
    private val controlSurfaceStore: PcControlSurfaceStore,
    private val mouseRepeatManager: PcMouseRepeatManager = PcMouseRepeatManager.instance
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
        controlSurfaceStore = PcControlSurfacePreferenceStore(context.applicationContext),
        mouseRepeatManager = PcMouseRepeatManager.instance.also { it.init(context.applicationContext) }
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
        val commandToSend = when (command) {
            is PcControlCommand.DragStart,
            is PcControlCommand.DragEnd -> if (_uiState.value.isDragging) {
                PcControlCommand.DragEnd()
            } else {
                PcControlCommand.DragStart()
            }
            else -> command
        }
        sendNoAckCommand(commandToSend) {
            it.copy(
                isDragging = when (commandToSend) {
                    is PcControlCommand.DragStart -> true
                    is PcControlCommand.DragEnd -> false
                    else -> it.isDragging
                },
                isBusy = false,
                busyCommand = null,
                message = null
            )
        }
    }

    fun sendMouseCommand(command: PcControlCommand, repeatable: Boolean) {
        if (repeatable && mouseRepeatManager.start(command, viewModelScope, ::sendRepeatCommand)) {
            return
        }
        send(command)
    }

    private suspend fun sendRepeatCommand(command: PcControlCommand): PcCommandResult {
        return sendNoAckCommandNow(command) {
            it.copy(
                isBusy = false,
                busyCommand = null,
                message = null
            )
        }
    }

    private fun sendNoAckCommand(
        command: PcControlCommand,
        onSent: (PcMouseControlUiState) -> PcMouseControlUiState = { it.copy(message = null) }
    ) {
        viewModelScope.launch {
            sendNoAckCommandNow(command, onSent)
        }
    }

    private suspend fun sendNoAckCommandNow(
        command: PcControlCommand,
        onSent: (PcMouseControlUiState) -> PcMouseControlUiState = { it.copy(message = null) }
    ): PcCommandResult {
        val controller = serviceControllerProvider()
        val state = controller?.state?.value
        if (controller == null || !controller.hasLiveControlSession()) {
            val message = if (state is PcServiceConnectionState.Reconnecting) RECONNECTING_MESSAGE else CONNECT_FIRST_MESSAGE
            showCommandBlocked(command, message)
            return PcCommandResult.Failed(message)
        }
        if (state is PcServiceConnectionState.Reconnecting || state is PcServiceConnectionState.OpeningControlSession) {
            showCommandBlocked(command, RECONNECTING_MESSAGE)
            return PcCommandResult.Failed(RECONNECTING_MESSAGE)
        }

        return when (val result = controller.sendRealtimeControlCommand(command)) {
            PcCommandResult.Ack -> {
                _uiState.update(onSent)
                result
            }
            is PcCommandResult.AuthFailed -> {
                _uiState.update {
                    it.copy(
                        message = result.message,
                        typingMessage = if (command is PcControlCommand.TypeText || command is PcControlCommand.PressKey) {
                            result.message
                        } else {
                            it.typingMessage
                        }
                    )
                }
                result
            }
            is PcCommandResult.Failed -> {
                _uiState.update {
                    it.copy(
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
                result
            }
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
        sendTypedTextInternal(sendEnterAfterText = false)
    }

    fun sendTypedTextThenEnter() {
        sendTypedTextInternal(sendEnterAfterText = true)
    }

    private fun sendTypedTextInternal(sendEnterAfterText: Boolean) {
        val text = _uiState.value.typingText
        validationMessageFor(text)?.let { message ->
            _uiState.update { it.copy(typingMessage = message) }
            return
        }
        if (text.isEmpty()) return

        val textCommand = PcControlCommand.TypeText(text)
        val controller = serviceControllerProvider()
        val state = controller?.state?.value
        if (controller == null || !controller.hasLiveControlSession()) {
            val message = if (state is PcServiceConnectionState.Reconnecting) RECONNECTING_MESSAGE else CONNECT_FIRST_MESSAGE
            showCommandBlocked(textCommand, message)
            return
        }
        if (state is PcServiceConnectionState.Reconnecting || state is PcServiceConnectionState.OpeningControlSession) {
            showCommandBlocked(textCommand, RECONNECTING_MESSAGE)
            return
        }
        if (_uiState.value.isBusy) return

        markTypingSendBusy(text)
        viewModelScope.launch {
            if (supportsTextStreams(controller, state)) {
                sendTypedTextStream(controller, text, sendEnterAfterText)
            } else {
                sendBulkTypedText(controller, textCommand, sendEnterAfterText)
            }
        }
    }

    private suspend fun sendBulkTypedText(
        controller: PcServiceConnectionController,
        textCommand: PcControlCommand.TypeText,
        sendEnterAfterText: Boolean
    ) {
        when (val textResult = controller.sendRealtimeControlCommand(textCommand)) {
            PcCommandResult.Ack -> {
                if (sendEnterAfterText) {
                    _uiState.update {
                        it.copy(
                            typingText = "",
                            typingMessage = null,
                            message = null
                        )
                    }
                    sendEnterAfterTypedText(controller)
                } else {
                    clearTypingSendSuccess()
                }
            }
            is PcCommandResult.AuthFailed -> clearTypingSendAuthFailure(textResult.message)
            is PcCommandResult.Failed -> clearTypingSendFailure(TYPING_FAILED_MESSAGE)
        }
    }

    private suspend fun sendTypedTextStream(
        controller: PcServiceConnectionController,
        text: String,
        sendEnterAfterText: Boolean
    ) {
        val streamId = "android-${UUID.randomUUID()}"
        val items = pcTextStreamItemsFor(text).toMutableList()
        if (sendEnterAfterText) {
            items += PcTextStreamItem.Key(PcKeyboardKey.Enter)
        }

        when (val openResult = sendTextStreamCommandWithReconnect(controller, PcControlCommand.TextStreamOpen(streamId))) {
            PcCommandResult.Ack -> Unit
            is PcCommandResult.AuthFailed -> {
                clearTypingSendAuthFailure(openResult.message)
                return
            }
            is PcCommandResult.Failed -> {
                clearTypingSendFailure(TYPING_FAILED_MESSAGE)
                return
            }
        }

        for ((index, item) in items.withIndex()) {
            val command = when (item) {
                is PcTextStreamItem.Chunk -> PcControlCommand.TextStreamChunk(streamId, index, item.text)
                is PcTextStreamItem.Key -> PcControlCommand.TextStreamKey(streamId, index, item.key)
            }
            when (val itemResult = sendTextStreamCommandWithReconnect(controller, command)) {
                PcCommandResult.Ack -> Unit
                is PcCommandResult.AuthFailed -> {
                    clearTypingSendAuthFailure(itemResult.message)
                    closeTextStreamBestEffort(controller, streamId, index)
                    return
                }
                is PcCommandResult.Failed -> {
                    clearTypingSendFailure(TYPING_FAILED_MESSAGE)
                    closeTextStreamBestEffort(controller, streamId, index)
                    return
                }
            }
            if (index < items.lastIndex) {
                delay(TEXT_STREAM_SEND_DELAY_MS)
            }
        }

        when (val closeResult = sendTextStreamCommandWithReconnect(controller, PcControlCommand.TextStreamClose(streamId, items.size))) {
            PcCommandResult.Ack -> clearTypingSendSuccess()
            is PcCommandResult.AuthFailed -> clearTypingSendAuthFailure(closeResult.message)
            is PcCommandResult.Failed -> clearTypingSendFailure(TYPING_FAILED_MESSAGE)
        }
    }

    private suspend fun sendTextStreamCommandWithReconnect(
        controller: PcServiceConnectionController,
        command: PcControlCommand,
        realtime: Boolean = false
    ): PcCommandResult {
        repeat(TEXT_STREAM_RECONNECT_RETRY_LIMIT + 1) { attempt ->
            val result = if (realtime) {
                controller.sendRealtimeControlCommand(command)
            } else {
                controller.sendControlCommand(command)
            }
            if (result !is PcCommandResult.Failed) {
                return result
            }
            if (attempt >= TEXT_STREAM_RECONNECT_RETRY_LIMIT || !shouldRetryTextStreamAfterFailure(controller)) {
                return result
            }
            if (!awaitTextStreamReconnect(controller)) {
                return result
            }
        }
        return PcCommandResult.Failed()
    }

    private fun shouldRetryTextStreamAfterFailure(controller: PcServiceConnectionController): Boolean {
        val state = controller.state.value
        return !controller.hasLiveControlSession() ||
                state is PcServiceConnectionState.Reconnecting ||
                state is PcServiceConnectionState.OpeningControlSession
    }

    private suspend fun awaitTextStreamReconnect(controller: PcServiceConnectionController): Boolean {
        return withTimeoutOrNull(TEXT_STREAM_RECONNECT_TIMEOUT_MS) {
            controller.state.first { state ->
                state is PcServiceConnectionState.Connected && controller.hasLiveControlSession()
            }
            true
        } ?: false
    }

    private suspend fun closeTextStreamBestEffort(
        controller: PcServiceConnectionController,
        streamId: String,
        processedCount: Int
    ) {
        runCatching {
            controller.sendControlCommand(PcControlCommand.TextStreamClose(streamId, processedCount))
        }
    }

    private fun supportsTextStreams(
        controller: PcServiceConnectionController,
        state: PcServiceConnectionState?
    ): Boolean {
        return (state as? PcServiceConnectionState.Connected)?.pointerProfile?.supportsTextStreams()
            ?: controller.currentPointerProfile()?.supportsTextStreams()
            ?: false
    }

    private suspend fun sendEnterAfterTypedText(controller: PcServiceConnectionController) {
        when (val keyResult = controller.sendRealtimeControlCommand(PcControlCommand.PressKey(PcKeyboardKey.Enter))) {
            PcCommandResult.Ack -> clearTypingSendAfterEnter()
            is PcCommandResult.AuthFailed -> clearTypingSendAuthFailure(keyResult.message)
            is PcCommandResult.Failed -> clearTypingSendFailure(KEY_FAILED_MESSAGE)
        }
    }

    private fun markTypingSendBusy(text: String) {
        _uiState.update {
            it.copy(
                isBusy = true,
                busyCommand = PcControlCommand.TypeText(text),
                typingMessage = null
            )
        }
    }

    private fun clearTypingSendSuccess() {
        _uiState.update {
            it.copy(
                isBusy = false,
                busyCommand = null,
                typingText = "",
                typingMessage = null,
                message = null
            )
        }
    }

    private fun clearTypingSendAfterEnter() {
        _uiState.update {
            it.copy(
                isBusy = false,
                busyCommand = null,
                typingMessage = null,
                message = null
            )
        }
    }

    private fun clearTypingSendFailure(
        typingMessage: String,
        message: String? = null
    ) {
        _uiState.update {
            it.copy(
                isBusy = false,
                busyCommand = null,
                message = message ?: it.message,
                typingMessage = typingMessage
            )
        }
    }

    private fun clearTypingSendAuthFailure(message: String) {
        clearTypingSendFailure(
            typingMessage = message,
            message = message
        )
    }

    fun sendKey(key: PcKeyboardKey) {
        sendNoAckCommand(PcControlCommand.PressKey(key)) {
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
        mouseRepeatManager.clearServiceState()
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
                val pointerProfile = state.pointerProfile ?: controller.currentPointerProfile()
                movementSteps = pointerProfile?.toMouseMovementSteps()
                    ?: controller.currentPointerProfile()?.toMouseMovementSteps()
                    ?: FALLBACK_MOVEMENT_STEPS
                _uiState.update {
                    it.copy(
                        connectedDisplayName = state.displayName,
                        movementStep = movementSteps.stepFor(it.selectedMovementSize),
                        supportsTextStreamInput = pointerProfile?.supportsTextStreams() ?: false,
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
                mouseRepeatManager.clearServiceState()
                _uiState.update {
                    it.copy(
                        connectedDisplayName = state.displayName,
                        isDragging = false,
                        supportsTextStreamInput = false,
                        message = RECONNECTING_MESSAGE,
                        connectionStatusText = RECONNECTING_MESSAGE
                    )
                }
            }
            PcServiceConnectionState.Disconnected -> {
                mouseRepeatManager.clearServiceState()
                movementSteps = FALLBACK_MOVEMENT_STEPS
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        movementStep = movementSteps.stepFor(it.selectedMovementSize),
                        isDragging = false,
                        isBusy = false,
                        busyCommand = null,
                        supportsTextStreamInput = false,
                        message = CONNECT_FIRST_MESSAGE,
                        connectionStatusText = null
                    )
                }
            }
            is PcServiceConnectionState.Failed -> {
                mouseRepeatManager.clearServiceState()
                movementSteps = FALLBACK_MOVEMENT_STEPS
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        movementStep = movementSteps.stepFor(it.selectedMovementSize),
                        isDragging = false,
                        isBusy = false,
                        busyCommand = null,
                        supportsTextStreamInput = false,
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
                mouseRepeatManager.clearServiceState()
                _uiState.update {
                    it.copy(
                        connectedDisplayName = state.displayName,
                        isDragging = false,
                        supportsTextStreamInput = false,
                        message = RECONNECTING_MESSAGE,
                        connectionStatusText = RECONNECTING_MESSAGE
                    )
                }
            }
            is PcConnectionState.Failed -> {
                mouseRepeatManager.clearServiceState()
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        isDragging = false,
                        isBusy = false,
                        busyCommand = null,
                        supportsTextStreamInput = false,
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
                isDragging = false,
                isBusy = false,
                busyCommand = null,
                supportsTextStreamInput = false,
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
        const val TEXT_STREAM_SEND_DELAY_MS = 250L
        const val TEXT_STREAM_RECONNECT_TIMEOUT_MS = 15_000L
        const val TEXT_STREAM_RECONNECT_RETRY_LIMIT = 3
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
