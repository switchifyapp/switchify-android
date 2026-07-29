package com.enaboapps.switchify.screens.pc

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcKeyboardModifierKey
import com.enaboapps.switchify.pc.PcKeyboardShortcutKey
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcMouseRepeatManager
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.pc.PcServiceSwitcherConnectionHost
import com.enaboapps.switchify.pc.PcSwitcherCoordinator
import com.enaboapps.switchify.pc.PcSwitcherUiState
import com.enaboapps.switchify.pc.PcTextStreamItem
import com.enaboapps.switchify.pc.isSafePcTypedText
import com.enaboapps.switchify.pc.pcTextStreamItemsFor
import com.enaboapps.switchify.pc.pointerMoveStep
import com.enaboapps.switchify.pc.supportsModifierToggle
import com.enaboapps.switchify.pc.supportsDisplayNavigation
import com.enaboapps.switchify.pc.supportsNoAckTextStreamChunks
import com.enaboapps.switchify.pc.supportsTextStreams
import com.enaboapps.switchify.pc.toShortcutKey
import com.enaboapps.switchify.service.core.ServiceCore
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
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
    val movementStep: Int = PcMouseControlViewModel.FALLBACK_MOVEMENT_STEP,
    val pointerSpeedSupported: Boolean = false,
    val pointerSpeedSetSupported: Boolean = false,
    val pointerSpeedScalePercent: Double = 100.0,
    val pointerSpeedMinScalePercent: Double = 5.0,
    val pointerSpeedMaxScalePercent: Double = 225.0,
    val pointerSpeedStepPercent: Double = 5.0,
    val pointerSpeedPercentLabel: String = PcMouseControlViewModel.POINTER_SPEED_UNAVAILABLE_MESSAGE,
    val displayNavigationSupported: Boolean = false,
    val displayCount: Int = 1,
    val isDragging: Boolean = false,
    val isBusy: Boolean = false,
    val busyCommand: PcControlCommand? = null,
    val message: String? = null,
    val typingText: String = "",
    val typingMessage: String? = null,
    val typingMode: PcTypingMode = PcTypingMode.Live,
    val liveTypingPaused: Boolean = false,
    val liveTypingMessage: String? = null,
    val supportsTextStreamInput: Boolean = false,
    val supportsModifierToggles: Boolean = false,
    val activeModifiers: Set<PcKeyboardModifierKey> = emptySet(),
    val connectionStatusText: String? = null
)

class PcMouseControlViewModel(
    private val serviceControllerProvider: () -> PcServiceConnectionController?,
    private val controlSurfaceStore: PcControlSurfaceStore,
    private val typingDraftStore: PcTypingDraftStore = InMemoryTypingDraftStore(),
    private val typingModeStore: PcTypingModeStore = InMemoryTypingModeStore(),
    private val mouseRepeatManager: PcMouseRepeatManager = PcMouseRepeatManager.instance
) : ViewModel() {
    constructor(
        serviceControllerProvider: () -> PcServiceConnectionController?
    ) : this(
        serviceControllerProvider = serviceControllerProvider,
        controlSurfaceStore = InMemoryControlSurfaceStore()
    )

    constructor(context: Context) : this(
        serviceControllerProvider = { ServiceCore.getPcServiceConnectionController() },
        controlSurfaceStore = PcControlSurfacePreferenceStore(context.applicationContext),
        typingDraftStore = PcTypingDraftPreferenceStore(context.applicationContext),
        typingModeStore = PcTypingModePreferenceStore(context.applicationContext),
        mouseRepeatManager = PcMouseRepeatManager.instance.also { it.init(context.applicationContext) }
    )

    private val _uiState = MutableStateFlow(PcMouseControlUiState())
    val uiState: StateFlow<PcMouseControlUiState> = _uiState.asStateFlow()
    private val pcSwitcher = PcSwitcherCoordinator(
        host = PcServiceSwitcherConnectionHost(serviceControllerProvider),
        scope = viewModelScope,
        beforeSwitch = ::prepareForPcSwitch
    )
    private val liveTypingCoordinator = PcLiveTypingCoordinator(
        scope = viewModelScope,
        sendCommand = { command ->
            val controller = serviceControllerProvider() ?: return@PcLiveTypingCoordinator PcCommandResult.Failed()
            sendTextStreamCommandWithReconnect(controller, command)
        },
        onFailure = { message ->
            _uiState.update {
                it.copy(
                    liveTypingPaused = true,
                    liveTypingMessage = message
                )
            }
        }
    )
    internal val pcSwitcherState: StateFlow<PcSwitcherUiState> = pcSwitcher.state
    private var movementStep = FALLBACK_MOVEMENT_STEP

    init {
        val selectedSurface = controlSurfaceStore.getSelectedSurface()
        val typingDraft = typingDraftStore.getDraft()
        val typingMode = typingModeStore.getMode()
        _uiState.update {
            it.copy(
                activeSurface = selectedSurface,
                movementStep = movementStep,
                typingText = typingDraft,
                typingMessage = validationMessageFor(typingDraft),
                typingMode = typingMode
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
                    else -> if (it.isDragging && commandToSend.endsActiveDragWithClick()) false else it.isDragging
                },
                isBusy = false,
                busyCommand = null,
                message = null
            )
        }
    }

    fun toggleModifier(key: PcKeyboardModifierKey) {
        val state = _uiState.value
        val isActive = state.activeModifiers.contains(key)
        val command = if (isActive) {
            PcControlCommand.ModifierUp(key)
        } else {
            PcControlCommand.ModifierDown(key)
        }
        debugLog(
            "Toggling PC modifier key=${key.protocolValue}, active=$isActive, advertised=${state.supportsModifierToggles}"
        )
        sendNoAckCommand(command) {
            it.copy(
                activeModifiers = if (isActive) {
                    it.activeModifiers - key
                } else {
                    it.activeModifiers + key
                },
                isBusy = false,
                busyCommand = null,
                message = null
            )
        }
    }

    fun sendShortcutKey(key: PcKeyboardShortcutKey) {
        val modifiers = orderedShortcutModifiers(_uiState.value.activeModifiers)
        if (modifiers.isEmpty()) {
            _uiState.update { it.copy(message = SELECT_SHORTCUT_MODIFIER_MESSAGE) }
            return
        }
        val keys = modifiers.map { it.toShortcutKey() } + key
        viewModelScope.launch {
            when (sendNoAckCommandNow(PcControlCommand.KeyboardShortcut(keys)) {
                it.copy(
                    isBusy = false,
                    busyCommand = null,
                    message = null
                )
            }) {
                PcCommandResult.Ack -> releaseActiveModifiersIfPossible()
                is PcCommandResult.AuthFailed,
                is PcCommandResult.Failed -> Unit
            }
        }
    }

    fun sendMouseCommand(command: PcControlCommand, repeatable: Boolean) {
        val mouseRepeat = currentMouseRepeatCapabilities()
        if (repeatable && mouseRepeat?.supported == true) {
            if (mouseRepeat.enabled) {
                sendPcSideRepeatCommand(command)
            } else {
                send(command)
            }
            return
        }

        if (repeatable && mouseRepeatManager.armForInitialSend(command)) {
            sendRepeatableMouseCommand(command)
            return
        }
        send(command)
    }

    fun setPointerSpeed(scalePercent: Double) {
        val state = _uiState.value
        if (!state.pointerSpeedSupported || !state.pointerSpeedSetSupported) {
            _uiState.update { it.copy(message = POINTER_SPEED_UNAVAILABLE_MESSAGE) }
            return
        }

        val normalized = normalizePointerSpeed(
            scalePercent,
            state.pointerSpeedMinScalePercent,
            state.pointerSpeedMaxScalePercent,
            state.pointerSpeedStepPercent
        )
        sendCommand(PcControlCommand.SetPointerSpeed(normalized)) {
            it.copy(
                pointerSpeedScalePercent = normalized,
                pointerSpeedPercentLabel = pointerSpeedLabel(normalized),
                isBusy = false,
                busyCommand = null,
                message = null
            )
        }
    }

    private fun sendPcSideRepeatCommand(command: PcControlCommand) {
        val controller = serviceControllerProvider()
        if (controller == null || !mouseRepeatManager.armPcSideRepeat(
            command = command,
            scope = viewModelScope,
            stopRepeatedCommand = { controller.sendControlCommand(PcControlCommand.RepeatStop) }
        )) {
            send(command)
            return
        }

        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            when (sendNoAckCommandNow(PcControlCommand.RepeatStart(command)) {
                it.copy(
                    isBusy = false,
                    busyCommand = null,
                    message = null
                )
            }) {
                PcCommandResult.Ack -> {
                    mouseRepeatManager.confirmPcSideStarted(command)
                }
                is PcCommandResult.AuthFailed,
                is PcCommandResult.Failed -> {
                    mouseRepeatManager.cancelPcSidePending(showMessage = false)
                }
            }
        }
    }

    private fun sendRepeatableMouseCommand(command: PcControlCommand) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            when (sendRepeatCommand(command)) {
                PcCommandResult.Ack -> {
                    mouseRepeatManager.startAfterInitialSend(
                        command = command,
                        scope = viewModelScope,
                        sendRepeatedCommand = ::sendRepeatCommand,
                        shouldPauseForReconnect = {
                            val controller = serviceControllerProvider()
                            controller?.state?.value is PcServiceConnectionState.Reconnecting ||
                                (controller?.state?.value is PcServiceConnectionState.Connected && !controller.hasLiveControlSession()) ||
                                PcConnectionStateHolder.connectionState.value is PcConnectionState.Reconnecting
                        }
                    )
                }
                is PcCommandResult.AuthFailed,
                is PcCommandResult.Failed -> {
                    mouseRepeatManager.cancelPendingStart(showMessage = false)
                }
            }
        }
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
                        activeModifiers = emptySet(),
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
        if (_uiState.value.activeSurface == PcControlSurface.Typing && surface != PcControlSurface.Typing) {
            stopLiveTyping()
        }
        controlSurfaceStore.setSelectedSurface(surface)
        _uiState.update {
            it.copy(
                activeSurface = surface,
                typingMessage = if (surface != PcControlSurface.Typing) null else it.typingMessage
            )
        }
    }

    fun selectTypingMode(mode: PcTypingMode) {
        val state = _uiState.value
        if (mode == PcTypingMode.Live && !state.supportsTextStreamInput) return
        typingModeStore.setMode(mode)
        if (mode == PcTypingMode.Draft) {
            stopLiveTyping()
        }
        _uiState.update {
            it.copy(
                typingMode = mode,
                liveTypingPaused = false,
                liveTypingMessage = null
            )
        }
    }

    fun startLiveTyping() {
        val state = _uiState.value
        if (state.typingMode != PcTypingMode.Live || !state.supportsTextStreamInput) return
        liveTypingCoordinator.start()
    }

    fun stopLiveTyping() {
        liveTypingCoordinator.finish()
    }

    fun commitLiveTypingText(text: String): Boolean {
        val accepted = liveTypingCoordinator.submitText(text)
        if (!accepted) {
            if (text.isNotEmpty() && !isSafePcTypedText(text)) {
                _uiState.update {
                    it.copy(liveTypingMessage = TEXT_UNSUPPORTED_MESSAGE)
                }
            }
        }
        return accepted
    }

    fun sendLiveTypingKey(key: PcKeyboardKey) {
        if (!liveTypingCoordinator.submitKey(key)) {
            _uiState.update {
                it.copy(
                    liveTypingPaused = true,
                    liveTypingMessage = PcLiveTypingCoordinator.LIVE_TYPING_FAILED_MESSAGE
                )
            }
        }
    }

    fun sendTypingKey(key: PcKeyboardKey) {
        val state = _uiState.value
        if (state.typingMode == PcTypingMode.Live && state.supportsTextStreamInput) {
            sendLiveTypingKey(key)
        } else {
            sendKey(key)
        }
    }

    fun retryLiveTyping() {
        _uiState.update {
            it.copy(
                liveTypingPaused = false,
                liveTypingMessage = null
            )
        }
        liveTypingCoordinator.resume()
    }

    fun showTypingSurface() {
        selectControlSurface(PcControlSurface.Typing)
    }

    fun showMouseSurface() {
        selectControlSurface(PcControlSurface.Mouse)
    }

    fun openSwitchPcChooser() {
        pcSwitcher.open()
    }

    fun dismissSwitchPcChooser() {
        pcSwitcher.dismiss()
    }

    fun refreshSwitchPcChoices() {
        pcSwitcher.refresh()
    }

    fun switchToPc(desktopId: String) {
        pcSwitcher.switchTo(desktopId)
    }

    fun cancelSwitchPcPairing() {
        pcSwitcher.cancelPairing()
    }

    private suspend fun prepareForPcSwitch() {
        val state = _uiState.value
        stopLiveTyping()
        mouseRepeatManager.clearServiceState()
        _uiState.update {
            it.copy(
                isDragging = false,
                activeModifiers = emptySet(),
                isBusy = false,
                busyCommand = null
            )
        }
        val controller = serviceControllerProvider()
        if (controller == null || !controller.hasLiveControlSession()) return
        if (state.isDragging) {
            controller.sendRealtimeControlCommand(PcControlCommand.DragEnd())
        }
        orderedShortcutModifiers(state.activeModifiers).asReversed().forEach { key ->
            controller.sendRealtimeControlCommand(PcControlCommand.ModifierUp(key))
        }
    }

    private fun releaseActiveModifiersIfPossible() {
        val modifiers = orderedShortcutModifiers(_uiState.value.activeModifiers).asReversed()
        if (modifiers.isEmpty()) return
        _uiState.update { it.copy(activeModifiers = emptySet()) }
        val controller = serviceControllerProvider()
        if (controller == null || !controller.hasLiveControlSession()) return

        viewModelScope.launch {
            modifiers.forEach { key ->
                controller.sendRealtimeControlCommand(PcControlCommand.ModifierUp(key))
            }
        }
    }

    fun updateTypingText(text: String) {
        typingDraftStore.setDraft(text)
        _uiState.update {
            it.copy(
                typingText = text,
                typingMessage = validationMessageFor(text)
            )
        }
    }

    fun clearTypingText() {
        typingDraftStore.clearDraft()
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
            if (supportsTextStreams(controller, state) && requiresTextStream(text)) {
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
        val textResult = if (sendEnterAfterText) {
            controller.sendControlCommand(textCommand)
        } else {
            controller.sendRealtimeControlCommand(textCommand)
        }
        when (textResult) {
            PcCommandResult.Ack -> {
                if (sendEnterAfterText) {
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
        val chunksCanUseNoAck = supportsNoAckTextStreamChunks(controller, controller.state.value)
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
            val realtime = chunksCanUseNoAck && item is PcTextStreamItem.Chunk
            when (val itemResult = sendTextStreamCommandWithReconnect(controller, command, realtime = realtime)) {
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

    private fun supportsNoAckTextStreamChunks(
        controller: PcServiceConnectionController,
        state: PcServiceConnectionState?
    ): Boolean {
        return (state as? PcServiceConnectionState.Connected)?.pointerProfile?.supportsNoAckTextStreamChunks()
            ?: controller.currentPointerProfile()?.supportsNoAckTextStreamChunks()
            ?: false
    }

    private fun requiresTextStream(text: String): Boolean {
        return text.any { it == '\n' || it == '\r' || it == '\t' }
    }

    private fun currentMouseRepeatCapabilities() =
        ((serviceControllerProvider()?.state?.value as? PcServiceConnectionState.Connected)?.pointerProfile
            ?: serviceControllerProvider()?.currentPointerProfile())
            ?.capabilities
            ?.mouseRepeat

    private suspend fun sendEnterAfterTypedText(controller: PcServiceConnectionController) {
        when (val keyResult = controller.sendControlCommand(PcControlCommand.PressKey(PcKeyboardKey.Enter))) {
            PcCommandResult.Ack -> clearTypingSendAfterEnter()
            is PcCommandResult.AuthFailed -> clearTypingSendFailureAfterTextSent(keyResult.message, keyResult.message)
            is PcCommandResult.Failed -> clearTypingSendFailureAfterTextSent(KEY_FAILED_MESSAGE)
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
        typingDraftStore.clearDraft()
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
        typingDraftStore.clearDraft()
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

    private fun clearTypingSendFailureAfterTextSent(
        typingMessage: String,
        message: String? = null
    ) {
        typingDraftStore.clearDraft()
        _uiState.update {
            it.copy(
                isBusy = false,
                busyCommand = null,
                typingText = "",
                message = message ?: it.message,
                typingMessage = typingMessage
            )
        }
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
        stopLiveTyping()
        releaseActiveModifiersIfPossible()
        serviceControllerProvider()?.onPcUiPaused()
    }

    fun stopPcBluetooth() {
        serviceControllerProvider()?.disconnect()
    }

    override fun onCleared() {
        stopLiveTyping()
        releaseActiveModifiersIfPossible()
        pcSwitcher.dispose()
        mouseRepeatManager.clearServiceState()
        serviceControllerProvider()?.onPcUiPaused()
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
                            activeModifiers = emptySet(),
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
                val supportsTextStreamInput = pointerProfile?.supportsTextStreams() ?: false
                val supportsModifierToggles = pointerProfile?.supportsModifierToggle() ?: false
                debugLog(
                    "Connected PC profile displayName=${state.displayName}, modifierTogglesAdvertised=$supportsModifierToggles, supportedCommands=${pointerProfile?.capabilities?.supportedCommands.orEmpty()}"
                )
                movementStep = pointerProfile?.pointerMoveStep()
                    ?: controller.currentPointerProfile()?.pointerMoveStep()
                    ?: FALLBACK_MOVEMENT_STEP
                _uiState.update {
                    it.copy(
                        connectedDisplayName = state.displayName,
                        movementStep = movementStep,
                        pointerSpeedSupported = pointerProfile?.capabilities?.pointerSpeed?.supported == true,
                        pointerSpeedSetSupported = pointerProfile?.capabilities?.pointerSpeed?.setSupported == true,
                        pointerSpeedScalePercent = pointerProfile?.capabilities?.pointerSpeed?.scalePercent ?: 100.0,
                        pointerSpeedMinScalePercent = pointerProfile?.capabilities?.pointerSpeed?.minScalePercent ?: 5.0,
                        pointerSpeedMaxScalePercent = pointerProfile?.capabilities?.pointerSpeed?.maxScalePercent ?: 225.0,
                        pointerSpeedStepPercent = pointerProfile?.capabilities?.pointerSpeed?.stepPercent ?: 5.0,
                        pointerSpeedPercentLabel = pointerSpeedLabel(pointerProfile?.capabilities?.pointerSpeed?.scalePercent),
                        displayNavigationSupported = pointerProfile?.supportsDisplayNavigation() == true,
                        displayCount = pointerProfile?.capabilities?.displayNavigation?.displayCount ?: 1,
                        supportsTextStreamInput = supportsTextStreamInput,
                        supportsModifierToggles = supportsModifierToggles,
                        message = if (it.message == RECONNECTING_MESSAGE || it.message == DISCONNECTED_MESSAGE || it.message == CONNECT_FIRST_MESSAGE) {
                            null
                        } else {
                            it.message
                        },
                        connectionStatusText = null
                    )
                }
                if (supportsTextStreamInput) {
                    liveTypingCoordinator.resume()
                } else {
                    stopLiveTyping()
                }
                mouseRepeatManager.resumeAfterReconnect(
                    scope = viewModelScope,
                    sendRepeatedCommand = ::sendRepeatCommand
                )
            }
            is PcServiceConnectionState.Reconnecting -> {
                mouseRepeatManager.pauseForReconnect(viewModelScope)
                _uiState.update {
                    it.copy(
                        connectedDisplayName = state.displayName,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        pointerSpeedSupported = false,
                        pointerSpeedSetSupported = false,
                        pointerSpeedPercentLabel = POINTER_SPEED_UNAVAILABLE_MESSAGE,
                        displayNavigationSupported = false,
                        displayCount = 1,
                        supportsModifierToggles = false,
                        message = RECONNECTING_MESSAGE,
                        connectionStatusText = RECONNECTING_MESSAGE
                    )
                }
            }
            PcServiceConnectionState.Disconnected -> {
                stopLiveTyping()
                mouseRepeatManager.clearServiceState()
                movementStep = FALLBACK_MOVEMENT_STEP
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        movementStep = movementStep,
                        pointerSpeedSupported = false,
                        pointerSpeedSetSupported = false,
                        pointerSpeedScalePercent = 100.0,
                        pointerSpeedPercentLabel = POINTER_SPEED_UNAVAILABLE_MESSAGE,
                        displayNavigationSupported = false,
                        displayCount = 1,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        isBusy = false,
                        busyCommand = null,
                        supportsTextStreamInput = false,
                        supportsModifierToggles = false,
                        message = CONNECT_FIRST_MESSAGE,
                        connectionStatusText = null
                    )
                }
            }
            is PcServiceConnectionState.Failed -> {
                stopLiveTyping()
                mouseRepeatManager.clearServiceState()
                movementStep = FALLBACK_MOVEMENT_STEP
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        movementStep = movementStep,
                        pointerSpeedSupported = false,
                        pointerSpeedSetSupported = false,
                        pointerSpeedScalePercent = 100.0,
                        pointerSpeedPercentLabel = POINTER_SPEED_UNAVAILABLE_MESSAGE,
                        displayNavigationSupported = false,
                        displayCount = 1,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        isBusy = false,
                        busyCommand = null,
                        supportsTextStreamInput = false,
                        supportsModifierToggles = false,
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
                mouseRepeatManager.pauseForReconnect(viewModelScope)
                _uiState.update {
                    it.copy(
                        connectedDisplayName = state.displayName,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        displayNavigationSupported = false,
                        displayCount = 1,
                        supportsModifierToggles = false,
                        message = RECONNECTING_MESSAGE,
                        connectionStatusText = RECONNECTING_MESSAGE
                    )
                }
            }
            is PcConnectionState.Failed -> {
                stopLiveTyping()
                mouseRepeatManager.clearServiceState()
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        isBusy = false,
                        busyCommand = null,
                        displayNavigationSupported = false,
                        displayCount = 1,
                        supportsTextStreamInput = false,
                        supportsModifierToggles = false,
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
        stopLiveTyping()
        movementStep = FALLBACK_MOVEMENT_STEP
        _uiState.update {
            it.copy(
                connectedDisplayName = null,
                movementStep = movementStep,
                pointerSpeedSupported = false,
                pointerSpeedSetSupported = false,
                pointerSpeedScalePercent = 100.0,
                pointerSpeedPercentLabel = POINTER_SPEED_UNAVAILABLE_MESSAGE,
                displayNavigationSupported = false,
                displayCount = 1,
                isDragging = false,
                activeModifiers = emptySet(),
                isBusy = false,
                busyCommand = null,
                supportsTextStreamInput = false,
                supportsModifierToggles = false,
                message = CONNECT_FIRST_MESSAGE,
                connectionStatusText = null
            )
        }
    }

    private fun pointerSpeedLabel(scalePercent: Double?): String {
        if (scalePercent == null) return POINTER_SPEED_UNAVAILABLE_MESSAGE
        val percent = if (scalePercent % 1.0 == 0.0) {
            scalePercent.toInt().toString()
        } else {
            String.format(Locale.ROOT, "%.1f", scalePercent)
        }
        return "$percent%"
    }

    private fun normalizePointerSpeed(value: Double, min: Double, max: Double, step: Double): Double {
        val bounded = value.coerceIn(min, max)
        val normalizedStep = if (step > 0.0) step else 5.0
        return kotlin.math.round(bounded / normalizedStep) * normalizedStep
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    companion object {
        private const val TAG = "PcMouseControlViewModel"
        const val FALLBACK_MOVEMENT_STEP = 80
        const val POINTER_SPEED_UNAVAILABLE_MESSAGE = "Update Switchify PC to set pointer speed."
        const val CONNECT_FIRST_MESSAGE = "Connect to PC from Switchify first."
        const val COMMAND_FAILED_MESSAGE = "Could not send command to PC."
        const val TYPING_FAILED_MESSAGE = "Could not send text to PC."
        const val KEY_FAILED_MESSAGE = "Could not send key to PC."
        const val TEXT_TOO_LONG_MESSAGE = "Text is too long."
        const val TEXT_UNSUPPORTED_MESSAGE = "Text includes unsupported characters."
        const val SELECT_SHORTCUT_MODIFIER_MESSAGE = "Choose Ctrl, Alt, Shift, or Start first."
        const val TEXT_STREAM_RECONNECT_TIMEOUT_MS = 15_000L
        const val TEXT_STREAM_RECONNECT_RETRY_LIMIT = 3
        const val RECONNECTING_MESSAGE = "Reconnecting..."
        const val BLUETOOTH_OFF_MESSAGE = "Bluetooth is off."
        const val BLUETOOTH_PERMISSION_DENIED_MESSAGE = "Bluetooth permission denied."
        const val DISCONNECTED_MESSAGE = "Disconnected."
        const val CONNECTING_MESSAGE = "Connecting to PC..."
        const val NO_PC_FOUND_MESSAGE = "No Switchify PC found."
        const val COULD_NOT_CONNECT_MESSAGE = "Could not connect to PC."
        const val REQUEST_REJECTED_MESSAGE = "Request rejected."
        const val REQUEST_EXPIRED_MESSAGE = "Request expired. Try again."
    }

    private fun validationMessageFor(text: String): String? {
        return when {
            text.length > PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH -> TEXT_TOO_LONG_MESSAGE
            !isSafePcTypedText(text) -> TEXT_UNSUPPORTED_MESSAGE
            else -> null
        }
    }
}

private fun PcControlCommand.endsActiveDragWithClick(): Boolean {
    return this is PcControlCommand.LeftClick ||
        this is PcControlCommand.DoubleClick ||
        this is PcControlCommand.RightClick
}

private class InMemoryControlSurfaceStore : PcControlSurfaceStore {
    private var surface = PcControlSurface.Mouse

    override fun getSelectedSurface(): PcControlSurface = surface

    override fun setSelectedSurface(surface: PcControlSurface) {
        this.surface = surface
    }
}

private class InMemoryTypingDraftStore : PcTypingDraftStore {
    private var draft = ""

    override fun getDraft(): String = draft

    override fun setDraft(text: String) {
        draft = text
    }

    override fun clearDraft() {
        draft = ""
    }
}

private class InMemoryTypingModeStore : PcTypingModeStore {
    private var mode = PcTypingMode.Live

    override fun getMode(): PcTypingMode = mode

    override fun setMode(mode: PcTypingMode) {
        this.mode = mode
    }
}
