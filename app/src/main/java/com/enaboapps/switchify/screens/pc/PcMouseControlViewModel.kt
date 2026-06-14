package com.enaboapps.switchify.screens.pc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnectionState
import com.enaboapps.switchify.pc.PcConnectionStateHolder
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcLiveControlResult
import com.enaboapps.switchify.pc.PcControlConnection
import com.enaboapps.switchify.pc.PcControlConnectionEvent
import com.enaboapps.switchify.pc.PcDeviceIdentityRepository
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcPairingTokenStore
import com.enaboapps.switchify.pc.PcTokenStore
import com.enaboapps.switchify.pc.PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH
import com.enaboapps.switchify.pc.bluetooth.SwitchifyPcBleClient
import com.enaboapps.switchify.pc.isSafePcTypedText
import com.enaboapps.switchify.pc.retryPcAuthFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    private val tokenStore: PcPairingTokenStore,
    private val connector: PcConnector,
    private val movementSizeStore: PcMouseMovementSizeStore,
    private val controlSurfaceStore: PcControlSurfaceStore
) : ViewModel() {
    constructor(
        tokenStore: PcPairingTokenStore,
        connector: PcConnector,
        movementSizeStore: PcMouseMovementSizeStore
    ) : this(
        tokenStore = tokenStore,
        connector = connector,
        movementSizeStore = movementSizeStore,
        controlSurfaceStore = InMemoryControlSurfaceStore()
    )

    constructor(context: Context) : this(
        tokenStore = PcTokenStore(context.applicationContext),
        connector = SwitchifyPcBleClient(
            context.applicationContext,
            PcDeviceIdentityRepository(context.applicationContext),
            PcTokenStore(context.applicationContext)
        ),
        movementSizeStore = PcMouseMovementPreferenceStore(context.applicationContext),
        controlSurfaceStore = PcControlSurfacePreferenceStore(context.applicationContext)
    )

    private val _uiState = MutableStateFlow(PcMouseControlUiState())
    val uiState: StateFlow<PcMouseControlUiState> = _uiState.asStateFlow()
    private var liveConnection: PcControlConnection? = null
    private var liveSession: PcAuthenticatedSession? = null
    private var liveConnectionDeferred: Deferred<PcControlConnection?>? = null
    private var liveConnectionEventsJob: Job? = null
    private var liveHeartbeatJob: Job? = null
    private var pendingUiPauseShutdownJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastSession: PcAuthenticatedSession? = null
    private var lastDisplayName: String? = null
    private var pcUiActive = false
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
        viewModelScope.launch {
            PcConnectionStateHolder.connectionState.collect { state ->
                val connectedDisplayName = when (state) {
                    is PcConnectionState.Connected -> state.displayName
                    is PcConnectionState.Reconnecting -> state.displayName
                    PcConnectionState.Disconnected,
                    is PcConnectionState.Failed -> null
                }
                _uiState.update {
                    it.copy(
                        connectedDisplayName = connectedDisplayName,
                        movementStep = if (state is PcConnectionState.Connected || state is PcConnectionState.Reconnecting) {
                            it.movementStep
                        } else {
                            fallbackStepFor(it.selectedMovementSize)
                        }
                    )
                }
                when (state) {
                    is PcConnectionState.Connected -> {
                        lastSession = state.session
                        lastDisplayName = state.displayName
                        ensureLiveConnection(state.session)
                    }
                    is PcConnectionState.Reconnecting -> Unit
                    is PcConnectionState.Failed -> closeLiveConnection()
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

    private fun sendCommand(command: PcControlCommand, onAck: (PcMouseControlUiState) -> PcMouseControlUiState) {
        val connectionState = PcConnectionStateHolder.connectionState.value
        val connected = connectionState as? PcConnectionState.Connected
        if (connected == null) {
            val message = if (connectionState is PcConnectionState.Reconnecting) RECONNECTING_MESSAGE else CONNECT_FIRST_MESSAGE
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
            return
        }
        if (_uiState.value.isBusy) return

        _uiState.update { it.copy(isBusy = true, busyCommand = command) }
        viewModelScope.launch {
            val result = sendWithLiveReconnect(connected.session, command)
            when (result) {
                PcCommandResult.Ack -> {
                    _uiState.update(onAck)
                }
                is PcCommandResult.AuthFailed -> {
                    tokenStore.clearToken(connected.session.desktopId)
                    PcConnectionStateHolder.setDisconnected()
                    _uiState.update {
                        it.copy(
                            connectedDisplayName = null,
                            isBusy = false,
                            busyCommand = null,
                            message = CONNECT_FIRST_MESSAGE,
                            typingMessage = CONNECT_FIRST_MESSAGE
                        )
                    }
                }
                is PcCommandResult.Failed -> {
                    closeLiveConnection()
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyCommand = null,
                            message = if (command is PcControlCommand.TypeText || command is PcControlCommand.PressKey) {
                                it.message
                            } else {
                                COMMAND_FAILED_MESSAGE
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

    private suspend fun sendWithLiveReconnect(
        session: PcAuthenticatedSession,
        command: PcControlCommand
    ): PcCommandResult {
        repeat(LIVE_COMMAND_ATTEMPTS) { attempt ->
            val result = sendWithCurrentOrNewConnection(session, command)
            if (result !is PcCommandResult.Failed) return result
            if (attempt < LIVE_COMMAND_ATTEMPTS - 1) closeLiveConnection()
        }
        return PcCommandResult.Failed()
    }

    private suspend fun sendWithCurrentOrNewConnection(
        session: PcAuthenticatedSession,
        command: PcControlCommand
    ): PcCommandResult {
        val connection = liveConnection ?: ensureLiveConnection(session).await()
        return if (connection == null) {
            PcCommandResult.Failed()
        } else {
            retryPcAuthFailure(
                block = { connection.sendCommand(command) },
                isAuthFailure = { it is PcCommandResult.AuthFailed }
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onPcUiResumed() {
        pcUiActive = true
        pendingUiPauseShutdownJob?.cancel()
        pendingUiPauseShutdownJob = null
        when (val state = PcConnectionStateHolder.connectionState.value) {
            is PcConnectionState.Connected -> {
                ensureLiveConnection(state.session)
                liveConnection?.let { startLiveHeartbeat(it, state.session) }
            }
            is PcConnectionState.Reconnecting -> reconnectSavedSession(state.session, state.displayName)
            PcConnectionState.Disconnected,
            is PcConnectionState.Failed -> {
                val session = lastSession
                val displayName = lastDisplayName
                if (session != null && displayName != null) reconnectSavedSession(session, displayName)
            }
        }
    }

    fun onPcUiPaused() {
        pcUiActive = false
        pendingUiPauseShutdownJob?.cancel()
        pendingUiPauseShutdownJob = viewModelScope.launch {
            delay(PC_CONTROL_UI_PAUSE_SHUTDOWN_GRACE_MS)
            stopPcBluetooth()
        }
    }

    fun stopPcBluetooth() {
        pcUiActive = false
        pendingUiPauseShutdownJob?.cancel()
        pendingUiPauseShutdownJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        closeLiveConnection()
        connector.close()
        PcConnectionStateHolder.setDisconnected()
        movementSteps = FALLBACK_MOVEMENT_STEPS
        _uiState.update {
            it.copy(
                connectedDisplayName = null,
                movementStep = movementSteps.stepFor(it.selectedMovementSize),
                isBusy = false,
                busyCommand = null,
                connectionStatusText = null
            )
        }
    }

    override fun onCleared() {
        stopPcBluetooth()
        super.onCleared()
    }

    private fun ensureLiveConnection(session: PcAuthenticatedSession): Deferred<PcControlConnection?> {
        liveConnection?.takeIf { liveSession == session }?.let {
            return CompletableDeferred(it)
        }
        liveConnectionDeferred?.takeIf { liveSession == session && it.isActive }?.let {
            return it
        }

        closeLiveConnection()
        liveSession = session
        return viewModelScope.async {
            when (val result = retryPcAuthFailure(
                block = { connector.openControlSession(session) },
                isAuthFailure = { it is PcLiveControlResult.AuthFailed }
            )) {
                is PcLiveControlResult.Connected -> {
                    liveConnection = result.connection
                    movementSteps = result.connection.pointerProfile?.toMouseMovementSteps() ?: FALLBACK_MOVEMENT_STEPS
                    _uiState.update {
                        it.copy(
                            movementStep = movementSteps.stepFor(it.selectedMovementSize),
                            message = if (it.message == RECONNECTING_MESSAGE || it.message == DISCONNECTED_MESSAGE) null else it.message,
                            connectionStatusText = null
                        )
                    }
                    observeLiveConnection(result.connection, session)
                    result.connection
                }
                is PcLiveControlResult.AuthFailed -> {
                    tokenStore.clearToken(session.desktopId)
                    clearLastSession(session.desktopId)
                    PcConnectionStateHolder.setDisconnected()
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
        liveConnectionEventsJob?.cancel()
        liveConnectionEventsJob = null
        liveHeartbeatJob?.cancel()
        liveHeartbeatJob = null
        liveConnection?.close()
        liveConnection = null
        liveSession = null
        liveConnectionDeferred?.cancel()
        liveConnectionDeferred = null
    }

    private fun observeLiveConnection(connection: PcControlConnection, session: PcAuthenticatedSession) {
        liveConnectionEventsJob?.cancel()
        liveConnectionEventsJob = viewModelScope.launch {
            connection.connectionEvents.collect { event ->
                when (event) {
                    PcControlConnectionEvent.Disconnected -> {
                        handleLiveConnectionFailed(session)
                    }
                }
            }
        }
        if (pcUiActive || pendingUiPauseShutdownJob?.isActive == true) {
            startLiveHeartbeat(connection, session)
        }
    }

    private fun startLiveHeartbeat(connection: PcControlConnection, session: PcAuthenticatedSession) {
        liveHeartbeatJob?.cancel()
        liveHeartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(LIVE_HEARTBEAT_INTERVAL_MS)
                when (val result = connection.checkHealth()) {
                    PcCommandResult.Ack -> Unit
                    is PcCommandResult.AuthFailed -> {
                        tokenStore.clearToken(session.desktopId)
                        clearLastSession(session.desktopId)
                        PcConnectionStateHolder.setDisconnected()
                        _uiState.update {
                            it.copy(
                                connectedDisplayName = null,
                                message = result.message,
                                connectionStatusText = null,
                                isBusy = false,
                                busyCommand = null
                            )
                        }
                        closeLiveConnection()
                        return@launch
                    }
                    is PcCommandResult.Failed -> {
                        handleLiveConnectionFailed(session)
                        return@launch
                    }
                }
            }
        }
    }

    private fun handleLiveConnectionFailed(session: PcAuthenticatedSession) {
        val displayName = lastDisplayName ?: _uiState.value.connectedDisplayName ?: "Switchify PC"
        viewModelScope.launch {
            closeLiveConnection()
            if (pcUiActive || pendingUiPauseShutdownJob?.isActive == true) {
                reconnectSavedSession(session, displayName)
            }
        }
    }

    private fun reconnectSavedSession(session: PcAuthenticatedSession, displayName: String) {
        if (reconnectJob?.isActive == true) return
        lastSession = session
        lastDisplayName = displayName
        reconnectJob = viewModelScope.launch {
            PcConnectionStateHolder.setReconnecting(session, displayName)
            _uiState.update {
                it.copy(
                    message = RECONNECTING_MESSAGE,
                    connectionStatusText = RECONNECTING_MESSAGE,
                    isBusy = false,
                    busyCommand = null
                )
            }
            for ((index, backoffMs) in RECONNECT_BACKOFF_MS.withIndex()) {
                closeLiveConnection()
                when (val result = connector.openControlSession(session)) {
                    is PcLiveControlResult.Connected -> {
                        liveConnection = result.connection
                        liveSession = session
                        movementSteps = result.connection.pointerProfile?.toMouseMovementSteps() ?: FALLBACK_MOVEMENT_STEPS
                        observeLiveConnection(result.connection, session)
                        PcConnectionStateHolder.setConnected(session, displayName)
                        _uiState.update {
                            it.copy(
                                connectedDisplayName = displayName,
                                movementStep = movementSteps.stepFor(it.selectedMovementSize),
                                message = null,
                                connectionStatusText = null,
                                isBusy = false,
                                busyCommand = null
                            )
                        }
                        return@launch
                    }
                    is PcLiveControlResult.AuthFailed -> {
                        tokenStore.clearToken(session.desktopId)
                        clearLastSession(session.desktopId)
                        PcConnectionStateHolder.setDisconnected()
                        _uiState.update {
                            it.copy(
                                connectedDisplayName = null,
                                message = result.message,
                                connectionStatusText = null,
                                isBusy = false,
                                busyCommand = null
                            )
                        }
                        return@launch
                    }
                    is PcLiveControlResult.Failed -> {
                        val safeMessage = safeReconnectMessage(result.message)
                        _uiState.update {
                            it.copy(
                                message = safeMessage,
                                connectionStatusText = safeMessage,
                                isBusy = false,
                                busyCommand = null
                            )
                        }
                    }
                }
                if (index < RECONNECT_BACKOFF_MS.lastIndex) delay(backoffMs)
            }
            closeLiveConnection()
            PcConnectionStateHolder.setFailed(DISCONNECTED_MESSAGE)
            _uiState.update {
                it.copy(
                    connectedDisplayName = null,
                    message = DISCONNECTED_MESSAGE,
                    connectionStatusText = DISCONNECTED_MESSAGE,
                    isBusy = false,
                    busyCommand = null
                )
            }
        }
    }

    private fun safeReconnectMessage(message: String): String {
        return when (message) {
            BLUETOOTH_OFF_MESSAGE,
            BLUETOOTH_PERMISSION_DENIED_MESSAGE -> message
            else -> RECONNECTING_MESSAGE
        }
    }

    private fun clearLastSession(desktopId: String) {
        if (lastSession?.desktopId == desktopId) {
            lastSession = null
            lastDisplayName = null
        }
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
        const val TYPING_FAILED_MESSAGE = "Could not send text to PC."
        const val KEY_FAILED_MESSAGE = "Could not send key to PC."
        const val TEXT_TOO_LONG_MESSAGE = "Text is too long."
        const val TEXT_UNSUPPORTED_MESSAGE = "Text includes unsupported characters."
        const val RECONNECTING_MESSAGE = "Reconnecting..."
        const val BLUETOOTH_OFF_MESSAGE = "Bluetooth is off."
        const val BLUETOOTH_PERMISSION_DENIED_MESSAGE = "Bluetooth permission denied."
        const val DISCONNECTED_MESSAGE = "Disconnected."
        private const val LIVE_COMMAND_ATTEMPTS = 3
        private const val LIVE_HEARTBEAT_INTERVAL_MS = 5_000L
        private const val PC_CONTROL_UI_PAUSE_SHUTDOWN_GRACE_MS = 8_000L
        private val RECONNECT_BACKOFF_MS = listOf(250L, 750L, 1_500L, 3_000L)
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
