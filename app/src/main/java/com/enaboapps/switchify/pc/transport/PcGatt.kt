package com.enaboapps.switchify.pc.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class PcGattAdvertisement(
    val peripheralId: String,
    val name: String?,
    val localName: String?,
    val rssi: Int?
)

enum class PcGattAdapterState {
    PoweredOn,
    PoweredOff,
    Unauthorized,
    Unsupported
}

interface PcGattAdapter {
    fun state(): PcGattAdapterState
    fun startScan(serviceUuid: UUID, onAdvertisement: (PcGattAdvertisement) -> Unit, onError: (Throwable) -> Unit)
    fun stopScan()
    suspend fun connect(peripheralId: String): PcGattConnection
}

interface PcGattConnection {
    val peripheralId: String
    val mtu: Int?
    fun isConnected(): Boolean
    suspend fun requestHighPriority()
    suspend fun requestMtu(mtu: Int): Int
    suspend fun discoverServices()
    suspend fun read(characteristic: UUID): ByteArray?
    suspend fun write(characteristic: UUID, value: ByteArray)
    suspend fun readDescriptor(characteristic: UUID, descriptor: UUID): ByteArray?
    fun monitor(characteristic: UUID, onValue: (ByteArray) -> Unit, onError: (Throwable) -> Unit): PcUnsubscribe
    fun onDisconnected(listener: () -> Unit): PcUnsubscribe
    suspend fun disconnect()
}

enum class PcGattOperationType {
    Mtu,
    DiscoverServices,
    ReadCharacteristic,
    WriteCharacteristic,
    ReadDescriptor,
    WriteDescriptor
}

data class PcGattOperationKey(val type: PcGattOperationType, val uuid: UUID? = null)

class PcGattOperationQueue(private val timeoutMs: Long) {
    private class Pending(val key: PcGattOperationKey, val result: CompletableDeferred<Any?>)

    private val mutex = Mutex()
    private val lock = Any()
    private var current: Pending? = null
    private var closed = false

    suspend fun <T> execute(key: PcGattOperationKey, start: () -> Boolean): T = mutex.withLock {
        val pending = Pending(key, CompletableDeferred())
        synchronized(lock) {
            if (closed) throw PcBluetoothException(CLOSED_MESSAGE)
            current = pending
        }
        try {
            if (!start()) throw PcBluetoothException("Bluetooth operation could not start.")
            @Suppress("UNCHECKED_CAST")
            withTimeout(timeoutMs) { pending.result.await() } as T
        } catch (_: TimeoutCancellationException) {
            throw PcBluetoothException("Bluetooth operation timed out.")
        } finally {
            synchronized(lock) {
                if (current === pending) current = null
            }
        }
    }

    fun complete(key: PcGattOperationKey, value: Any?): Boolean = take(key)?.result?.complete(value) ?: false

    fun fail(key: PcGattOperationKey, error: Throwable): Boolean =
        take(key)?.result?.completeExceptionally(error) ?: false

    fun close() {
        val pending = synchronized(lock) {
            closed = true
            current.also { current = null }
        }
        pending?.result?.completeExceptionally(PcBluetoothException(CLOSED_MESSAGE))
    }

    private fun take(key: PcGattOperationKey): Pending? = synchronized(lock) {
        current?.takeIf { it.key == key }?.also { current = null }
    }

    private companion object {
        const val CLOSED_MESSAGE = "Bluetooth connection closed."
    }
}
