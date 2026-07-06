package com.enaboapps.switchify.screens.pc

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH
import com.enaboapps.switchify.pc.PcApprovalCodeState
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcErrorReason
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcKeyboardModifierKey
import com.enaboapps.switchify.pc.PcKeyboardShortcutKey
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcMouseRepeatManager
import com.enaboapps.switchify.pc.PcServiceConnectResult
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.pc.PcTextStreamItem
import com.enaboapps.switchify.pc.isSafePcTypedText
import com.enaboapps.switchify.pc.pcTextStreamItemsFor
import com.enaboapps.switchify.pc.pointerMoveStep
import com.enaboapps.switchify.pc.supportsModifierToggle
import com.enaboapps.switchify.pc.supportsTextStreams
import com.enaboapps.switchify.pc.toShortcutKey
import com.enaboapps.switchify.service.core.ServiceCore
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PcSwitchRowState(
    val desktopId: String,
    val displayName: String,
    val summary: String,
    val connected: Boolean,
    val enabled: Boolean
)

data class PcMouseControlUiState(
    val connectedDisplayName: String? = null,
    val switcherConnectedDisplayName: String? = null,
    val activeSurface: PcControlSurface = PcControlSurface.Mouse,
    val movementStep: Int = PcMouseControlViewModel.FALLBACK_MOVEMENT_STEP,
    val pointerSpeedSupported: Boolean = false,
    val pointerSpeedSetSupported: Boolean = false,
    val pointerSpeedScalePercent: Double = 100.0,
    val pointerSpeedMinScalePercent: Double = 5.0,
    val pointerSpeedMaxScalePercent: Double = 225.0,
    val pointerSpeedStepPercent: Double = 5.0,
    val pointerSpeedPercentLabel: String = PcMouseControlViewModel.POINTER_SPEED_UNAVAILABLE_MESSAGE,
    val isDragging: Boolean = false,
    val isBusy: Boolean = false,
    val busyCommand: PcControlCommand? = null,
    val message: String? = null,
    val typingText: String = "",
    val typingMessage: String? = null,
    val supportsTextStreamInput: Boolean = false,
    val supportsModifierToggles: Boolean = false,
    val activeModifiers: Set<PcKeyboardModifierKey> = emptySet(),
    val connectionStatusText: String? = null,
    val switchPcChooserVisible: Boolean = false,
    val switchPcRows: List<PcSwitchRowState> = emptyList(),
    val isDiscoveringSwitchPcs: Boolean = false,
    val switchingDesktopId: String? = null,
    val switchPcApprovalCode: PcApprovalCodeState? = null
)

class PcMouseControlViewModel(
    private val serviceControllerProvider: () -> PcServiceConnectionController?,
    private val controlSurfaceStore: PcControlSurfaceStore,
    private val typingDraftStore: PcTypingDraftStore = InMemoryTypingDraftStore(),
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
        mouseRepeatManager = PcMouseRepeatManager.instance.also { it.init(context.applicationContext) }
    )

    private val _uiState = MutableStateFlow(PcMouseControlUiState())
    val uiState: StateFlow<PcMouseControlUiState> = _uiState.asStateFlow()
    private var movementStep = FALLBACK_MOVEMENT_STEP
    private var switchPcCandidates: List<DiscoveredPc> = emptyList()
    private var switchPcConnectionJob: Job? = null

    init {
        val selectedSurface = controlSurfaceStore.getSelectedSurface()
        val typingDraft = typingDraftStore.getDraft()
        _uiState.update {
            it.copy(
                activeSurface = selectedSurface,
                movementStep = movementStep,
                typingText = typingDraft,
                typingMessage = validationMessageFor(typingDraft)
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

    fun sendShortcutLetter(letter: PcKeyboardShortcutKey) {
        val modifiers = orderedShortcutModifiers(_uiState.value.activeModifiers)
        if (modifiers.isEmpty()) {
            _uiState.update { it.copy(message = SELECT_SHORTCUT_MODIFIER_MESSAGE) }
            return
        }
        val keys = modifiers.map { it.toShortcutKey() } + letter
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

    fun openSwitchPcChooser() {
        _uiState.update {
            it.copy(
                switchPcChooserVisible = true,
                switchPcApprovalCode = null,
                switchingDesktopId = null
            )
        }
        refreshSwitchPcChoices()
    }

    fun dismissSwitchPcChooser() {
        clearActiveSwitchPcConnection()
        _uiState.update {
            it.copy(
                switchPcChooserVisible = false,
                switchPcApprovalCode = null,
                switchingDesktopId = null,
                isDiscoveringSwitchPcs = false
            )
        }
    }

    fun refreshSwitchPcChoices() {
        val controller = serviceControllerProvider()
        if (controller == null) {
            switchPcCandidates = emptyList()
            _uiState.update {
                it.copy(
                    message = CONNECT_FIRST_MESSAGE,
                    switchPcRows = emptyList(),
                    isDiscoveringSwitchPcs = false,
                    switchingDesktopId = null
                )
            }
            return
        }
        _uiState.update { it.copy(isDiscoveringSwitchPcs = true) }
        viewModelScope.launch {
            val pcs = controller.discoverPairedPcs()
            switchPcCandidates = pcs
            _uiState.update {
                val switcherName = currentConnectedDesktopId()?.let { connectedDesktopId ->
                    pcs.firstOrNull { pc -> pc.desktopId == connectedDesktopId }?.controlDeviceName
                } ?: it.switcherConnectedDisplayName
                it.copy(
                    switcherConnectedDisplayName = switcherName,
                    switchPcRows = switchRowsFor(pcs, it),
                    isDiscoveringSwitchPcs = false
                )
            }
        }
    }

    fun switchToPc(desktopId: String) {
        val pc = switchPcCandidates.firstOrNull { it.desktopId == desktopId }
        if (pc == null) {
            _uiState.update {
                it.copy(message = NO_PC_FOUND_MESSAGE)
            }
            refreshSwitchPcChoices()
            return
        }
        if (isConnectedDesktop(desktopId)) {
            dismissSwitchPcChooser()
            return
        }
        val controller = serviceControllerProvider()
        if (controller == null) {
            _uiState.update {
                it.copy(message = CONNECT_FIRST_MESSAGE)
            }
            return
        }
        releaseActiveModifiersIfPossible()
        mouseRepeatManager.clearServiceState()
        switchPcConnectionJob?.cancel()
        _uiState.update {
            it.copy(
                isDragging = false,
                activeModifiers = emptySet(),
                switchingDesktopId = desktopId,
                switchPcApprovalCode = null,
                message = CONNECTING_MESSAGE,
                switchPcRows = switchRowsFor(switchPcCandidates, it.copy(switchingDesktopId = desktopId, isDragging = false))
            )
        }
        switchPcConnectionJob = viewModelScope.launch {
            when (val result = controller.connectTo(pc) { approvalCode ->
                _uiState.update { it.copy(switchPcApprovalCode = approvalCode) }
            }) {
                is PcServiceConnectResult.Connected -> {
                    switchPcConnectionJob = null
                    _uiState.update {
                        it.copy(
                            switchPcChooserVisible = false,
                            switchingDesktopId = null,
                            switchPcApprovalCode = null,
                            message = null,
                            switchPcRows = switchRowsFor(switchPcCandidates, it.copy(switchingDesktopId = null))
                        )
                    }
                }
                is PcServiceConnectResult.Failed -> {
                    switchPcConnectionJob = null
                    _uiState.update {
                        val next = it.copy(
                            switchingDesktopId = null,
                            switchPcApprovalCode = null,
                            message = switchPcFailureMessage(result.reason)
                        )
                        next.copy(switchPcRows = switchRowsFor(switchPcCandidates, next))
                    }
                }
            }
        }
    }

    fun cancelSwitchPcPairing() {
        clearActiveSwitchPcConnection()
        _uiState.update {
            val next = it.copy(
                switchingDesktopId = null,
                switchPcApprovalCode = null,
                message = null
            )
            next.copy(switchPcRows = switchRowsFor(switchPcCandidates, next))
        }
    }

    private fun clearActiveSwitchPcConnection() {
        switchPcConnectionJob?.cancel()
        switchPcConnectionJob = null
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

    private fun currentMouseRepeatCapabilities() =
        ((serviceControllerProvider()?.state?.value as? PcServiceConnectionState.Connected)?.pointerProfile
            ?: serviceControllerProvider()?.currentPointerProfile())
            ?.capabilities
            ?.mouseRepeat

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
        releaseActiveModifiersIfPossible()
        serviceControllerProvider()?.onPcUiPaused()
    }

    fun stopPcBluetooth() {
        serviceControllerProvider()?.disconnect()
    }

    override fun onCleared() {
        releaseActiveModifiersIfPossible()
        clearActiveSwitchPcConnection()
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
                        switcherConnectedDisplayName = controller.currentControlDeviceName() ?: state.displayName,
                        movementStep = movementStep,
                        pointerSpeedSupported = pointerProfile?.capabilities?.pointerSpeed?.supported == true,
                        pointerSpeedSetSupported = pointerProfile?.capabilities?.pointerSpeed?.setSupported == true,
                        pointerSpeedScalePercent = pointerProfile?.capabilities?.pointerSpeed?.scalePercent ?: 100.0,
                        pointerSpeedMinScalePercent = pointerProfile?.capabilities?.pointerSpeed?.minScalePercent ?: 5.0,
                        pointerSpeedMaxScalePercent = pointerProfile?.capabilities?.pointerSpeed?.maxScalePercent ?: 225.0,
                        pointerSpeedStepPercent = pointerProfile?.capabilities?.pointerSpeed?.stepPercent ?: 5.0,
                        pointerSpeedPercentLabel = pointerSpeedLabel(pointerProfile?.capabilities?.pointerSpeed?.scalePercent),
                        supportsTextStreamInput = pointerProfile?.supportsTextStreams() ?: false,
                        supportsModifierToggles = supportsModifierToggles,
                        message = if (it.message == RECONNECTING_MESSAGE || it.message == DISCONNECTED_MESSAGE || it.message == CONNECT_FIRST_MESSAGE) {
                            null
                        } else {
                            it.message
                        },
                        connectionStatusText = null,
                        switchPcRows = switchRowsFor(
                            switchPcCandidates,
                            it.copy(
                                connectedDisplayName = state.displayName,
                                switcherConnectedDisplayName = controller.currentControlDeviceName() ?: state.displayName
                            )
                        )
                    )
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
                        switcherConnectedDisplayName = controller.currentControlDeviceName() ?: state.displayName,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        pointerSpeedSupported = false,
                        pointerSpeedSetSupported = false,
                        pointerSpeedPercentLabel = POINTER_SPEED_UNAVAILABLE_MESSAGE,
                        supportsTextStreamInput = false,
                        supportsModifierToggles = false,
                        message = RECONNECTING_MESSAGE,
                        connectionStatusText = RECONNECTING_MESSAGE,
                        switchPcRows = switchRowsFor(
                            switchPcCandidates,
                            it.copy(
                                connectedDisplayName = state.displayName,
                                switcherConnectedDisplayName = controller.currentControlDeviceName() ?: state.displayName
                            )
                        )
                    )
                }
            }
            PcServiceConnectionState.Disconnected -> {
                mouseRepeatManager.clearServiceState()
                movementStep = FALLBACK_MOVEMENT_STEP
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        switcherConnectedDisplayName = null,
                        movementStep = movementStep,
                        pointerSpeedSupported = false,
                        pointerSpeedSetSupported = false,
                        pointerSpeedScalePercent = 100.0,
                        pointerSpeedPercentLabel = POINTER_SPEED_UNAVAILABLE_MESSAGE,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        isBusy = false,
                        busyCommand = null,
                        supportsTextStreamInput = false,
                        supportsModifierToggles = false,
                        message = CONNECT_FIRST_MESSAGE,
                        connectionStatusText = null,
                        switchPcRows = switchRowsFor(
                            switchPcCandidates,
                            it.copy(connectedDisplayName = null, switcherConnectedDisplayName = null)
                        )
                    )
                }
            }
            is PcServiceConnectionState.Failed -> {
                mouseRepeatManager.clearServiceState()
                movementStep = FALLBACK_MOVEMENT_STEP
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        switcherConnectedDisplayName = null,
                        movementStep = movementStep,
                        pointerSpeedSupported = false,
                        pointerSpeedSetSupported = false,
                        pointerSpeedScalePercent = 100.0,
                        pointerSpeedPercentLabel = POINTER_SPEED_UNAVAILABLE_MESSAGE,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        isBusy = false,
                        busyCommand = null,
                        supportsTextStreamInput = false,
                        supportsModifierToggles = false,
                        message = state.message,
                        connectionStatusText = state.message,
                        switchPcRows = switchRowsFor(
                            switchPcCandidates,
                            it.copy(connectedDisplayName = null, switcherConnectedDisplayName = null)
                        )
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
                        switcherConnectedDisplayName = state.displayName,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        supportsTextStreamInput = false,
                        supportsModifierToggles = false,
                        message = RECONNECTING_MESSAGE,
                        connectionStatusText = RECONNECTING_MESSAGE,
                        switchPcRows = switchRowsFor(
                            switchPcCandidates,
                            it.copy(connectedDisplayName = state.displayName, switcherConnectedDisplayName = state.displayName)
                        )
                    )
                }
            }
            is PcConnectionState.Failed -> {
                mouseRepeatManager.clearServiceState()
                _uiState.update {
                    it.copy(
                        connectedDisplayName = null,
                        switcherConnectedDisplayName = null,
                        isDragging = false,
                        activeModifiers = emptySet(),
                        isBusy = false,
                        busyCommand = null,
                        supportsTextStreamInput = false,
                        supportsModifierToggles = false,
                        message = state.message,
                        connectionStatusText = state.message,
                        switchPcRows = switchRowsFor(
                            switchPcCandidates,
                            it.copy(connectedDisplayName = null, switcherConnectedDisplayName = null)
                        )
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
        movementStep = FALLBACK_MOVEMENT_STEP
        _uiState.update {
            it.copy(
                connectedDisplayName = null,
                switcherConnectedDisplayName = null,
                movementStep = movementStep,
                pointerSpeedSupported = false,
                pointerSpeedSetSupported = false,
                pointerSpeedScalePercent = 100.0,
                pointerSpeedPercentLabel = POINTER_SPEED_UNAVAILABLE_MESSAGE,
                isDragging = false,
                activeModifiers = emptySet(),
                isBusy = false,
                busyCommand = null,
                supportsTextStreamInput = false,
                supportsModifierToggles = false,
                message = CONNECT_FIRST_MESSAGE,
                connectionStatusText = null,
                switchPcRows = switchRowsFor(
                    switchPcCandidates,
                    it.copy(connectedDisplayName = null, switcherConnectedDisplayName = null)
                )
            )
        }
    }

    private fun switchRowsFor(
        pcs: List<DiscoveredPc>,
        state: PcMouseControlUiState
    ): List<PcSwitchRowState> {
        val connectedDesktopId = currentConnectedDesktopId()
        return pcs.map { pc ->
            val connected = pc.desktopId == connectedDesktopId
            PcSwitchRowState(
                desktopId = pc.desktopId,
                displayName = pc.controlDeviceName,
                summary = pc.primaryAddress,
                connected = connected,
                enabled = !connected && !state.isBusy && state.switchingDesktopId == null
            )
        }
    }

    private fun isConnectedDesktop(desktopId: String): Boolean {
        return currentConnectedDesktopId() == desktopId
    }

    private fun currentConnectedDesktopId(): String? {
        return when (val connectionState = PcConnectionStateHolder.connectionState.value) {
            is PcConnectionState.Connected -> connectionState.session.desktopId
            is PcConnectionState.Reconnecting -> connectionState.session.desktopId
            else -> (serviceControllerProvider()?.state?.value as? PcServiceConnectionState.Connected)?.session?.desktopId
        }
    }

    private fun switchPcFailureMessage(reason: PcErrorReason): String {
        return when (reason) {
            PcErrorReason.NoPcFound -> NO_PC_FOUND_MESSAGE
            PcErrorReason.PairingRejected -> REQUEST_REJECTED_MESSAGE
            PcErrorReason.PairingRequestExpired -> REQUEST_EXPIRED_MESSAGE
            else -> COULD_NOT_CONNECT_MESSAGE
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
        const val TEXT_STREAM_SEND_DELAY_MS = 250L
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
