package com.enaboapps.switchify.pc.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.enaboapps.switchify.pc.protocol.PcBleUuids
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

@SuppressLint("MissingPermission")
class AndroidPcGattAdapter(
    context: Context,
    private val operationTimeoutMs: Long = PcBleTransport.DEFAULT_NATIVE_TIMEOUT_MS
) : PcGattAdapter {
    private val context = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var scanCallback: ScanCallback? = null

    override fun state(): PcGattAdapterState {
        val adapter = bluetoothAdapter ?: return PcGattAdapterState.Unsupported
        if (!PcBluetoothPermissions.hasAll(context)) return PcGattAdapterState.Unauthorized
        return if (adapter.isEnabled) PcGattAdapterState.PoweredOn else PcGattAdapterState.PoweredOff
    }

    @Synchronized
    override fun startScan(
        serviceUuid: UUID,
        onAdvertisement: (PcGattAdvertisement) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        stopScan()
        ensureUsable()
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: throw PcBluetoothException("Bluetooth is off.")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onAdvertisement(result.toAdvertisement())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onAdvertisement(it.toAdvertisement()) }
            }

            override fun onScanFailed(errorCode: Int) {
                onError(PcBluetoothException("Bluetooth scanning failed."))
            }
        }
        scanCallback = callback
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback
        )
    }

    @Synchronized
    override fun stopScan() {
        val callback = scanCallback ?: return
        scanCallback = null
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
        }
    }

    override suspend fun connect(peripheralId: String): PcGattConnection {
        ensureUsable()
        val adapter = bluetoothAdapter ?: throw PcBluetoothException("Bluetooth is unavailable.")
        val device = try {
            adapter.getRemoteDevice(peripheralId)
        } catch (error: IllegalArgumentException) {
            throw PcBluetoothException("Bluetooth device is unavailable.", error)
        }
        val connection = AndroidPcGattConnection(peripheralId, operationTimeoutMs)
        val gatt = device.connectGatt(context, false, connection.callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw PcBluetoothException("Bluetooth connection failed.")
        connection.attach(gatt)
        try {
            connection.connected.await()
        } catch (error: Throwable) {
            connection.disconnect()
            if (error is CancellationException || error is PcBluetoothException) throw error
            throw PcBluetoothException("Bluetooth connection failed.", error)
        }
        return connection
    }

    private fun ensureUsable() {
        when (state()) {
            PcGattAdapterState.PoweredOn -> Unit
            PcGattAdapterState.Unauthorized -> throw PcBluetoothException("Bluetooth permission is missing.")
            PcGattAdapterState.PoweredOff -> throw PcBluetoothException("Bluetooth is off.")
            PcGattAdapterState.Unsupported -> throw PcBluetoothException("Bluetooth is unavailable.")
        }
    }

    private fun ScanResult.toAdvertisement() = PcGattAdvertisement(
        peripheralId = device.address,
        name = try {
            device.name
        } catch (_: SecurityException) {
            null
        },
        localName = scanRecord?.deviceName,
        rssi = rssi
    )
}

@SuppressLint("MissingPermission")
private class AndroidPcGattConnection(
    override val peripheralId: String,
    operationTimeoutMs: Long
) : PcGattConnection {
    private val queue = PcGattOperationQueue(operationTimeoutMs)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val disconnectListeners = CopyOnWriteArraySet<() -> Unit>()
    private val notificationListeners = CopyOnWriteArraySet<Pair<UUID, (ByteArray) -> Unit>>()
    private lateinit var gatt: BluetoothGatt
    val connected = CompletableDeferred<Unit>()

    @Volatile
    private var isOpen = false

    @Volatile
    private var closed = false

    @Volatile
    override var mtu: Int? = null
        private set

    fun attach(gatt: BluetoothGatt) {
        this.gatt = gatt
    }

    override fun isConnected(): Boolean = isOpen && !closed

    override suspend fun requestHighPriority() {
        if (!gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)) {
            throw PcBluetoothException("Bluetooth connection priority could not be requested.")
        }
    }

    override suspend fun requestMtu(mtu: Int): Int =
        queue.execute(PcGattOperationKey(PcGattOperationType.Mtu)) { gatt.requestMtu(mtu) }

    override suspend fun discoverServices() {
        queue.execute<Unit>(PcGattOperationKey(PcGattOperationType.DiscoverServices)) { gatt.discoverServices() }
    }

    override suspend fun read(characteristic: UUID): ByteArray? {
        val target = characteristic(characteristic)
        return queue.execute(PcGattOperationKey(PcGattOperationType.ReadCharacteristic, characteristic)) {
            gatt.readCharacteristic(target)
        }
    }

    override suspend fun write(characteristic: UUID, value: ByteArray) {
        val target = characteristic(characteristic)
        queue.execute<Unit>(PcGattOperationKey(PcGattOperationType.WriteCharacteristic, characteristic)) {
            val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(target, value, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                target.value = value
                target.writeType = writeType
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(target)
            }
        }
    }

    override suspend fun readDescriptor(characteristic: UUID, descriptor: UUID): ByteArray? {
        val target = descriptor(characteristic, descriptor)
        return queue.execute(PcGattOperationKey(PcGattOperationType.ReadDescriptor, descriptor)) {
            gatt.readDescriptor(target)
        }
    }

    override fun monitor(
        characteristic: UUID,
        onValue: (ByteArray) -> Unit,
        onError: (Throwable) -> Unit
    ): PcUnsubscribe {
        val target = characteristic(characteristic)
        val listener = characteristic to onValue
        notificationListeners += listener
        val setup = scope.launch {
            try {
                if (!gatt.setCharacteristicNotification(target, true)) {
                    throw PcBluetoothException("Bluetooth notifications could not be enabled.")
                }
                writeDescriptor(characteristic, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error as? PcBluetoothException ?: PcBluetoothException("Bluetooth notifications could not be enabled."))
            }
        }
        return PcUnsubscribe {
            notificationListeners -= listener
            setup.cancel()
            if (isConnected()) {
                scope.launch {
                    try {
                        gatt.setCharacteristicNotification(target, false)
                        writeDescriptor(characteristic, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    override fun onDisconnected(listener: () -> Unit): PcUnsubscribe {
        disconnectListeners += listener
        return PcUnsubscribe { disconnectListeners -= listener }
    }

    override suspend fun disconnect() {
        close()
    }

    private fun close() {
        if (closed) return
        closed = true
        isOpen = false
        queue.close()
        scope.cancel()
        if (!connected.isCompleted) connected.completeExceptionally(PcBluetoothException("Bluetooth connection closed."))
        if (::gatt.isInitialized) {
            try {
                gatt.disconnect()
            } catch (_: Exception) {
            }
            try {
                gatt.close()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun writeDescriptor(characteristic: UUID, value: ByteArray) {
        val target = descriptor(characteristic, PcBleUuids.clientCharacteristicConfiguration)
        queue.execute<Unit>(PcGattOperationKey(PcGattOperationType.WriteDescriptor, target.uuid)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(target, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                target.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(target)
            }
        }
    }

    private fun characteristic(uuid: UUID): BluetoothGattCharacteristic =
        gatt.getService(PcBleUuids.service)?.getCharacteristic(uuid)
            ?: throw PcBluetoothException("Bluetooth characteristic is unavailable.")

    private fun descriptor(characteristic: UUID, uuid: UUID): BluetoothGattDescriptor =
        characteristic(characteristic).getDescriptor(uuid)
            ?: throw PcBluetoothException("Bluetooth descriptor is unavailable.")

    private fun deliverNotification(uuid: UUID, value: ByteArray) {
        notificationListeners.filter { it.first == uuid }.forEach { it.second(value.copyOf()) }
    }

    val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                isOpen = true
                connected.complete(Unit)
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val wasOpen = isOpen
                val wasClosed = closed
                if (!connected.isCompleted) {
                    connected.completeExceptionally(PcBluetoothException("Bluetooth connection failed."))
                }
                close()
                if (wasOpen && !wasClosed) disconnectListeners.forEach { it() }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val key = PcGattOperationKey(PcGattOperationType.Mtu)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                this@AndroidPcGattConnection.mtu = mtu
                queue.complete(key, mtu)
            } else {
                queue.fail(key, PcBluetoothException("Bluetooth MTU negotiation failed."))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val key = PcGattOperationKey(PcGattOperationType.DiscoverServices)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                queue.complete(key, Unit)
            } else {
                queue.fail(key, PcBluetoothException("Bluetooth service discovery failed."))
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            completeRead(characteristic.uuid, value, status)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                completeRead(characteristic.uuid, characteristic.value ?: ByteArray(0), status)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val key = PcGattOperationKey(PcGattOperationType.WriteCharacteristic, characteristic.uuid)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                queue.complete(key, Unit)
            } else {
                queue.fail(key, PcBluetoothException("Bluetooth write failed."))
            }
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
            value: ByteArray
        ) {
            completeDescriptorRead(descriptor.uuid, value, status)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                completeDescriptorRead(descriptor.uuid, descriptor.value ?: ByteArray(0), status)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val key = PcGattOperationKey(PcGattOperationType.WriteDescriptor, descriptor.uuid)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                queue.complete(key, Unit)
            } else {
                queue.fail(key, PcBluetoothException("Bluetooth notifications could not be enabled."))
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            deliverNotification(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                characteristic.value?.let { deliverNotification(characteristic.uuid, it) }
            }
        }

        private fun completeRead(uuid: UUID, value: ByteArray, status: Int) {
            val key = PcGattOperationKey(PcGattOperationType.ReadCharacteristic, uuid)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                queue.complete(key, value.copyOf())
            } else {
                queue.fail(key, PcBluetoothException("Bluetooth read failed."))
            }
        }

        private fun completeDescriptorRead(uuid: UUID, value: ByteArray, status: Int) {
            val key = PcGattOperationKey(PcGattOperationType.ReadDescriptor, uuid)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                queue.complete(key, value.copyOf())
            } else {
                queue.fail(key, PcBluetoothException("Bluetooth read failed."))
            }
        }
    }
}
