package com.enaboapps.switchify.pc.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeout

class PcBleGattTransportFactory(private val context: Context) : PcBleTransportFactory {
    override suspend fun connect(endpoint: PcBluetoothEndpoint): PcBleTransportConnection {
        return PcBleGattConnection.connect(context.applicationContext, endpoint)
    }
}

private class PcBleGattConnection private constructor(
    override val endpoint: PcBluetoothEndpoint,
    private val gatt: BluetoothGatt,
    private val writer: PcBleGattWriter,
    private val responseRouter: PcBleResponseRouter,
    override val events: Flow<PcBleTransportEvent>,
    private val onClose: () -> Unit
) : PcBleTransportConnection {
    private var closed = false

    override suspend fun send(message: String, writeMode: PcBleWriteMode) {
        try {
            writer.send(message, writeMode)
        } catch (error: Throwable) {
            close("write_failed")
            throw error
        }
    }

    override suspend fun sendAndReceive(message: String, requestId: String, timeoutMs: Long): String {
        val response = responseRouter.register(requestId)
        return try {
            send(message, PcBleWriteMode.WithResponse)
            withTimeout(timeoutMs) { response.await() }
        } finally {
            responseRouter.unregister(requestId, response)
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestHighPriority(enabled: Boolean): Boolean {
        if (closed) return false
        val priority = if (enabled) {
            BluetoothGatt.CONNECTION_PRIORITY_HIGH
        } else {
            BluetoothGatt.CONNECTION_PRIORITY_BALANCED
        }
        return gatt.requestConnectionPriority(priority)
    }

    override fun close(reason: String) {
        if (closed) return
        closed = true
        Log.d(TAG, "PC BLE GATT closing endpoint=${endpoint.deviceAddress} reason=$reason")
        onClose()
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
        responseRouter.fail(
            PcBleTransportException(PcBleFailureReason.ConnectionClosed, "Bluetooth connection closed.")
        )
        writer.fail()
    }

    companion object {
        suspend fun connect(context: Context, endpoint: PcBluetoothEndpoint): PcBleGattConnection {
            if (!context.hasBluetoothConnectPermission()) {
                throw PcBleTransportException(PcBleFailureReason.PermissionMissing, "Bluetooth permission missing.")
            }
            if (!context.isBluetoothEnabled()) {
                throw PcBleTransportException(PcBleFailureReason.BluetoothDisabled, "Bluetooth is off.")
            }
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val device = manager.adapter.getRemoteDevice(endpoint.deviceAddress)
            val callback = ConnectionCallback()
            @SuppressLint("MissingPermission")
            val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            callback.gatt = gatt
            return runCatching { callback.awaitConnected(context, endpoint) }
                .getOrElse {
                    runCatching { gatt.disconnect() }
                    runCatching { gatt.close() }
                    throw it
                }
        }
    }

    private class ConnectionCallback : BluetoothGattCallback() {
        lateinit var gatt: BluetoothGatt
        private val connected = CompletableDeferred<Unit>()
        private val servicesDiscovered = CompletableDeferred<Unit>()
        private val notificationsEnabled = CompletableDeferred<Unit>()
        private val responseRouter = PcBleResponseRouter()
        private val events = MutableSharedFlow<PcBleTransportEvent>(extraBufferCapacity = 8)
        private val reassembler = BluetoothFrameReassembler()
        private val setupCoordinator = PcBleGattSetupCoordinator()
        private lateinit var writer: PcBleGattWriter
        private var pendingDescriptorWrite: BluetoothGattDescriptor? = null
        private var setupComplete = false
        private var closedByClient = false
        private var deviceAddress = "unknown"

        @SuppressLint("MissingPermission")
        suspend fun awaitConnected(context: Context, endpoint: PcBluetoothEndpoint): PcBleGattConnection {
            deviceAddress = endpoint.deviceAddress
            withTimeout(GATT_CONNECT_TIMEOUT_MS) { connected.await() }
            val serviceDiscoveryStarted = setupCoordinator.startServiceDiscovery(
                requestMtu = gatt::requestMtu,
                discoverServices = gatt::discoverServices
            )
            if (!serviceDiscoveryStarted) {
                throw PcBleTransportException(
                    PcBleFailureReason.ServiceDiscoveryFailed,
                    "Could not discover Bluetooth services."
                )
            }
            withTimeout(GATT_CONNECT_TIMEOUT_MS) { servicesDiscovered.await() }

            val service = gatt.getService(PcBleConstants.serviceUuid)
                ?: throw PcBleTransportException(PcBleFailureReason.ServiceMissing, "Switchify BLE service missing.")
            val rx = service.getCharacteristic(PcBleConstants.rxCharacteristicUuid)
                ?: throw PcBleTransportException(
                    PcBleFailureReason.CharacteristicMissing,
                    "Switchify BLE RX characteristic missing."
                )
            val tx = service.getCharacteristic(PcBleConstants.txCharacteristicUuid)
                ?: throw PcBleTransportException(
                    PcBleFailureReason.CharacteristicMissing,
                    "Switchify BLE TX characteristic missing."
                )
            writer = PcBleGattWriter(gatt, rx)
            enableNotifications(tx)
            withTimeout(GATT_NOTIFY_TIMEOUT_MS) { notificationsEnabled.await() }
            setupComplete = true

            return PcBleGattConnection(
                endpoint = endpoint,
                gatt = gatt,
                writer = writer,
                responseRouter = responseRouter,
                events = events.asSharedFlow(),
                onClose = {
                    closedByClient = true
                    writer.fail()
                }
            )
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                connected.complete(Unit)
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                val disconnected = PcBleTransportException(
                    PcBleFailureReason.ConnectionClosed,
                    "Bluetooth disconnected."
                )
                if (!connected.isCompleted) connected.completeExceptionally(disconnected)
                if (setupComplete && !closedByClient) {
                    Log.d(TAG, "PC BLE GATT disconnected unexpectedly status=$status endpoint=$deviceAddress")
                    events.tryEmit(PcBleTransportEvent.Disconnected)
                }
                responseRouter.fail(disconnected)
                if (::writer.isInitialized) writer.fail()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesDiscovered.complete(Unit)
            } else {
                servicesDiscovered.completeExceptionally(
                    PcBleTransportException(
                        PcBleFailureReason.ServiceDiscoveryFailed,
                        "Bluetooth service discovery failed."
                    )
                )
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            setupCoordinator.onMtuChanged(status == BluetoothGatt.GATT_SUCCESS)
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
            if (::writer.isInitialized) {
                writer.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor == pendingDescriptorWrite && status == BluetoothGatt.GATT_SUCCESS) {
                notificationsEnabled.complete(Unit)
            } else if (descriptor == pendingDescriptorWrite) {
                notificationsEnabled.completeExceptionally(
                    PcBleTransportException(
                        PcBleFailureReason.NotificationSetupFailed,
                        "Bluetooth notification setup failed."
                    )
                )
            }
            pendingDescriptorWrite = null
        }

        private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
            @SuppressLint("MissingPermission")
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                throw PcBleTransportException(
                    PcBleFailureReason.NotificationSetupFailed,
                    "Could not enable Bluetooth notifications."
                )
            }
            val descriptor = characteristic.getDescriptor(PcBleConstants.clientCharacteristicConfigUuid)
                ?: throw PcBleTransportException(
                    PcBleFailureReason.NotificationSetupFailed,
                    "Bluetooth notification descriptor missing."
                )
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
            if (!started) {
                throw PcBleTransportException(
                    PcBleFailureReason.NotificationSetupFailed,
                    "Could not write Bluetooth notification descriptor."
                )
            }
        }

        private fun handleNotification(value: ByteArray) {
            val frame = BluetoothFrameCodec.decode(value) ?: return
            when (val result = reassembler.accept(frame)) {
                is BluetoothFrameReassemblyResult.Complete -> responseRouter.route(result.message)
                BluetoothFrameReassemblyResult.Incomplete -> Unit
                is BluetoothFrameReassemblyResult.Rejected -> Unit
            }
        }

    }
}

private const val GATT_CONNECT_TIMEOUT_MS = 10_000L
private const val GATT_NOTIFY_TIMEOUT_MS = 5_000L
private const val TAG = "PcBleGattClient"
