package com.enaboapps.switchify.pc.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

interface PcBleTransportFactory {
    suspend fun connect(endpoint: PcBluetoothEndpoint): PcBleTransportConnection
}

interface PcBleTransportConnection {
    val endpoint: PcBluetoothEndpoint
    val events: Flow<PcBleTransportEvent>
    suspend fun sendAndReceive(message: String, timeoutMs: Long): String
    fun close(reason: String = "client_close")
}

sealed class PcBleTransportEvent {
    data object Disconnected : PcBleTransportEvent()
}

class PcBleGattTransportFactory(private val context: Context) : PcBleTransportFactory {
    override suspend fun connect(endpoint: PcBluetoothEndpoint): PcBleTransportConnection {
        return PcBleGattConnection.connect(context.applicationContext, endpoint)
    }
}

private class PcBleGattConnection private constructor(
    override val endpoint: PcBluetoothEndpoint,
    private val gatt: BluetoothGatt,
    private val rxCharacteristic: BluetoothGattCharacteristic,
    private val incomingMessages: Channel<String>,
    private val writeRequests: Channel<GattWriteRequest>,
    override val events: Flow<PcBleTransportEvent>,
    private val onClose: () -> Unit
) : PcBleTransportConnection {
    private val writeMutex = Mutex()
    private var closed = false

    override suspend fun sendAndReceive(message: String, timeoutMs: Long): String {
        for (frame in BluetoothFrameCodec.createFrames(message)) {
            writeFrame(frame)
        }
        return withTimeout(timeoutMs) { incomingMessages.receive() }
    }

    override fun close(reason: String) {
        if (closed) return
        closed = true
        Log.d(TAG, "PC BLE GATT closing endpoint=${endpoint.deviceAddress} reason=$reason")
        onClose()
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
        incomingMessages.close()
        writeRequests.close()
    }

    private suspend fun writeFrame(frame: BluetoothFrame) {
        writeMutex.withLock {
            val completion = CompletableDeferred<Boolean>()
            writeRequests.send(GattWriteRequest(rxCharacteristic, BluetoothFrameCodec.encode(frame), completion))
            if (!completion.await()) throw IllegalStateException("Bluetooth write failed.")
        }
    }

    companion object {
        suspend fun connect(context: Context, endpoint: PcBluetoothEndpoint): PcBleGattConnection {
            if (!context.hasBluetoothConnectPermission()) throw IllegalStateException("Bluetooth permission missing.")
            if (!context.isBluetoothEnabled()) throw IllegalStateException("Bluetooth is off.")
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val device = manager.adapter.getRemoteDevice(endpoint.deviceAddress)
            val callback = ConnectionCallback()
            @SuppressLint("MissingPermission")
            val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            callback.gatt = gatt
            return callback.awaitConnected(context, endpoint)
        }
    }

    private class ConnectionCallback : BluetoothGattCallback() {
        lateinit var gatt: BluetoothGatt
        private val connected = CompletableDeferred<Unit>()
        private val servicesDiscovered = CompletableDeferred<Unit>()
        private val notificationsEnabled = CompletableDeferred<Unit>()
        private val incomingMessages = Channel<String>(Channel.BUFFERED)
        private val writeRequests = Channel<GattWriteRequest>(Channel.UNLIMITED)
        private val events = MutableSharedFlow<PcBleTransportEvent>(extraBufferCapacity = 8)
        private val reassembler = BluetoothFrameReassembler()
        private var pendingDescriptorWrite: BluetoothGattDescriptor? = null
        private var pendingWrite: GattWriteRequest? = null
        private var setupComplete = false
        private var closedByClient = false
        private var deviceAddress = "unknown"

        suspend fun awaitConnected(context: Context, endpoint: PcBluetoothEndpoint): PcBleGattConnection {
            deviceAddress = endpoint.deviceAddress
            withTimeout(GATT_CONNECT_TIMEOUT_MS) { connected.await() }
            @SuppressLint("MissingPermission")
            if (!gatt.discoverServices()) throw IllegalStateException("Could not discover Bluetooth services.")
            withTimeout(GATT_CONNECT_TIMEOUT_MS) { servicesDiscovered.await() }

            val service = gatt.getService(PcBleConstants.serviceUuid) ?: throw IllegalStateException("Switchify BLE service missing.")
            val rx = service.getCharacteristic(PcBleConstants.rxCharacteristicUuid)
                ?: throw IllegalStateException("Switchify BLE RX characteristic missing.")
            val tx = service.getCharacteristic(PcBleConstants.txCharacteristicUuid)
                ?: throw IllegalStateException("Switchify BLE TX characteristic missing.")
            enableNotifications(tx)
            withTimeout(GATT_NOTIFY_TIMEOUT_MS) { notificationsEnabled.await() }
            setupComplete = true

            CoroutineScope(Dispatchers.IO).launch {
                for (request in writeRequests) {
                    pendingWrite = request
                    @SuppressLint("MissingPermission")
                    val started = if (Build.VERSION.SDK_INT >= 33) {
                        gatt.writeCharacteristic(request.characteristic, request.value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        request.characteristic.value = request.value
                        request.characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(request.characteristic)
                    }
                    if (!started) {
                        pendingWrite = null
                        request.completion.complete(false)
                    }
                }
            }

            return PcBleGattConnection(
                endpoint = endpoint,
                gatt = gatt,
                rxCharacteristic = rx,
                incomingMessages = incomingMessages,
                writeRequests = writeRequests,
                events = events.asSharedFlow(),
                onClose = { closedByClient = true }
            )
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                connected.complete(Unit)
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                if (!connected.isCompleted) connected.completeExceptionally(IllegalStateException("Bluetooth disconnected."))
                if (setupComplete && !closedByClient) {
                    Log.d(TAG, "PC BLE GATT disconnected unexpectedly status=$status endpoint=$deviceAddress")
                    events.tryEmit(PcBleTransportEvent.Disconnected)
                }
                incomingMessages.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesDiscovered.complete(Unit)
            } else {
                servicesDiscovered.completeExceptionally(IllegalStateException("Bluetooth service discovery failed."))
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotification(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.completion?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingWrite = null
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor == pendingDescriptorWrite && status == BluetoothGatt.GATT_SUCCESS) {
                notificationsEnabled.complete(Unit)
            } else if (descriptor == pendingDescriptorWrite) {
                notificationsEnabled.completeExceptionally(IllegalStateException("Bluetooth notification setup failed."))
            }
            pendingDescriptorWrite = null
        }

        private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
            @SuppressLint("MissingPermission")
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                throw IllegalStateException("Could not enable Bluetooth notifications.")
            }
            val descriptor = characteristic.getDescriptor(PcBleConstants.clientCharacteristicConfigUuid)
                ?: throw IllegalStateException("Bluetooth notification descriptor missing.")
            pendingDescriptorWrite = descriptor
            @SuppressLint("MissingPermission")
            val started = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            if (!started) throw IllegalStateException("Could not write Bluetooth notification descriptor.")
        }

        private fun handleNotification(value: ByteArray) {
            val frame = BluetoothFrameCodec.decode(value) ?: return
            when (val result = reassembler.accept(frame)) {
                is BluetoothFrameReassemblyResult.Complete -> incomingMessages.trySend(result.message)
                BluetoothFrameReassemblyResult.Incomplete -> Unit
                is BluetoothFrameReassemblyResult.Rejected -> Unit
            }
        }
    }
}

private data class GattWriteRequest(
    val characteristic: BluetoothGattCharacteristic,
    val value: ByteArray,
    val completion: CompletableDeferred<Boolean>
)

internal fun Context.hasBluetoothScanPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

internal fun Context.hasBluetoothConnectPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

internal fun Context.isBluetoothEnabled(): Boolean {
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return manager.adapter?.isEnabled == true
}

private const val GATT_CONNECT_TIMEOUT_MS = 10_000L
private const val GATT_NOTIFY_TIMEOUT_MS = 5_000L
private const val TAG = "PcBleGattClient"
