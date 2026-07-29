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
    fun requestHighPriority(enabled: Boolean): Boolean
    fun close(reason: String = "client_close")
}

internal class HighPriorityPcBleTransportFactory(
    private val delegate: PcBleTransportFactory
) : PcBleTransportFactory {
    override suspend fun connect(endpoint: PcBluetoothEndpoint): PcBleTransportConnection {
        return HighPriorityPcBleTransportConnection(delegate.connect(endpoint))
    }
}

private class HighPriorityPcBleTransportConnection(
    private val delegate: PcBleTransportConnection
) : PcBleTransportConnection {
    override val endpoint: PcBluetoothEndpoint = delegate.endpoint
    override val events: Flow<PcBleTransportEvent> = delegate.events
    private val closeLock = Any()
    private var closed = false

    init {
        delegate.requestHighPriority(true)
    }

    override suspend fun send(message: String, writeMode: PcBleWriteMode) {
        delegate.send(message, writeMode)
    }

    override suspend fun sendAndReceive(message: String, requestId: String, timeoutMs: Long): String {
        return delegate.sendAndReceive(message, requestId, timeoutMs)
    }

    override fun requestHighPriority(enabled: Boolean): Boolean {
        return delegate.requestHighPriority(enabled)
    }

    override fun close(reason: String) {
        val shouldClose = synchronized(closeLock) {
            if (closed) false else {
                closed = true
                true
            }
        }
        if (!shouldClose) return
        delegate.requestHighPriority(false)
        delegate.close(reason)
    }
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
