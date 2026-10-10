package com.enaboapps.switchify.pc.transport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

class PcGattOperationQueue(
    private val timeoutMs: Long,
    private val onTimedOut: () -> Unit = {}
) {
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
            val started = try {
                start()
            } catch (error: SecurityException) {
                throw PcBluetoothException(PERMISSION_MESSAGE, error)
            }
            if (!started) throw PcBluetoothException("Bluetooth operation could not start.")
            val outcome = withContext(NonCancellable) {
                withTimeoutOrNull(timeoutMs) {
                    try {
                        Result.success(pending.result.await())
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                }
            }
            if (outcome == null) {
                close()
                onTimedOut()
            }
            currentCoroutineContext().ensureActive()
            if (outcome == null) throw PcBluetoothException("Bluetooth operation timed out.")
            @Suppress("UNCHECKED_CAST")
            outcome.getOrThrow() as T
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
        const val PERMISSION_MESSAGE = "Bluetooth permission is missing."
    }
}

class PcNotificationSubscriber(
    private val scope: CoroutineScope,
    private val setNotification: (Boolean) -> Boolean,
    private val writeConfiguration: suspend (ByteArray) -> Unit
) {
    fun enable(onError: (Throwable) -> Unit): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            if (!setNotification(true)) throw PcBluetoothException(ENABLE_FAILED_MESSAGE)
            writeConfiguration(ENABLE_NOTIFICATION_VALUE)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(error as? PcBluetoothException ?: PcBluetoothException(ENABLE_FAILED_MESSAGE, error))
        }
    }

    fun disable(): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            setNotification(false)
            writeConfiguration(DISABLE_NOTIFICATION_VALUE)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }

    companion object {
        val ENABLE_NOTIFICATION_VALUE = byteArrayOf(0x01, 0x00)
        val DISABLE_NOTIFICATION_VALUE = byteArrayOf(0x00, 0x00)
        private const val ENABLE_FAILED_MESSAGE = "Bluetooth notifications could not be enabled."
    }
}
