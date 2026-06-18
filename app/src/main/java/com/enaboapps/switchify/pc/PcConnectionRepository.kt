package com.enaboapps.switchify.pc

import kotlinx.coroutines.flow.StateFlow

interface PcConnectionRepository {
    val connectionState: StateFlow<PcConnectionState>
    suspend fun requestAccess(pc: DiscoveredPc): Result<Unit>
    suspend fun connectWithSavedToken(pc: DiscoveredPc): Result<Unit>
    suspend fun disconnect()
}

data class PcAuthenticatedSession(
    val desktopId: String,
    val deviceId: String,
    val endpointId: String,
    val transport: PcTransport = PcTransport.Bluetooth
)

enum class PcTransport {
    Bluetooth
}

sealed class PcConnectionState {
    data object Disconnected : PcConnectionState()
    data class Reconnecting(val session: PcAuthenticatedSession, val displayName: String) : PcConnectionState()
    data class Connected(val session: PcAuthenticatedSession, val displayName: String) : PcConnectionState()
    data class Failed(val message: String) : PcConnectionState()
}

object PcConnectionStateHolder {
    private val _connectionState = kotlinx.coroutines.flow.MutableStateFlow<PcConnectionState>(PcConnectionState.Disconnected)
    val connectionState: StateFlow<PcConnectionState> = _connectionState

    fun setConnected(session: PcAuthenticatedSession, displayName: String) {
        _connectionState.value = PcConnectionState.Connected(session, displayName)
    }

    fun setReconnecting(session: PcAuthenticatedSession, displayName: String) {
        _connectionState.value = PcConnectionState.Reconnecting(session, displayName)
    }

    fun setFailed(message: String) {
        _connectionState.value = PcConnectionState.Failed(message)
    }

    fun setDisconnected() {
        _connectionState.value = PcConnectionState.Disconnected
    }
}
