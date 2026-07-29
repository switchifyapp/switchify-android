package com.enaboapps.switchify.pc.bluetooth

import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import kotlinx.coroutines.flow.Flow

interface PcBleTransportFactory {
    suspend fun connect(endpoint: PcBluetoothEndpoint): PcBleTransportConnection
}

interface PcBleTransportConnection {
    val endpoint: PcBluetoothEndpoint
    val events: Flow<PcBleTransportEvent>
    suspend fun send(message: String, writeMode: PcBleWriteMode = PcBleWriteMode.WithResponse)
    suspend fun sendAndReceive(message: String, requestId: String, timeoutMs: Long): String
    fun requestLowLatency(enabled: Boolean): Boolean
    fun close(reason: String = "client_close")
}

enum class PcBleWriteMode {
    WithResponse,
    WithoutResponse
}

sealed class PcBleTransportEvent {
    data object Disconnected : PcBleTransportEvent()
}

internal enum class PcBleFailureReason {
    PermissionMissing,
    BluetoothDisabled,
    ConnectionClosed,
    ServiceDiscoveryFailed,
    ServiceMissing,
    CharacteristicMissing,
    NotificationSetupFailed,
    WriteTimedOut,
    WriteFailed
}

internal class PcBleTransportException(
    val reason: PcBleFailureReason,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
