package com.enaboapps.switchify.pc.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcDiscovery
import com.enaboapps.switchify.pc.PcDiscoveryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class PcBleDiscoveryService(private val context: Context) : PcDiscovery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val discovered = linkedMapOf<String, DiscoveredPc>()
    private val resolvingAddresses = mutableSetOf<String>()
    private var scanCallback: ScanCallback? = null

    private val _pcs = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    override val pcs: StateFlow<List<DiscoveredPc>> = _pcs

    private val _status = MutableStateFlow(PcDiscoveryStatus.Idle)
    override val status: StateFlow<PcDiscoveryStatus> = _status

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        stopDiscovery()
        discovered.clear()
        resolvingAddresses.clear()
        _pcs.value = emptyList()
        _status.value = PcDiscoveryStatus.Searching

        if (!context.hasBluetoothScanPermission() || !context.hasBluetoothConnectPermission()) {
            _status.value = PcDiscoveryStatus.Failed
            return
        }
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (scanner == null) {
            _status.value = PcDiscoveryStatus.Failed
            return
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch { resolve(result) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> scope.launch { resolve(result) } }
            }

            override fun onScanFailed(errorCode: Int) {
                _status.value = PcDiscoveryStatus.Failed
            }
        }
        scanCallback = callback
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(PcBleConstants.serviceUuid))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching { scanner.startScan(filters, settings, callback) }
            .onFailure { _status.value = PcDiscoveryStatus.Failed }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        val callback = scanCallback ?: return
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        runCatching { scanner?.stopScan(callback) }
        scanCallback = null
        if (_pcs.value.isEmpty() && _status.value == PcDiscoveryStatus.Searching) {
            _status.value = PcDiscoveryStatus.Empty
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolve(result: ScanResult) {
        if (!context.hasBluetoothConnectPermission()) return
        val device = result.device ?: return
        if (!resolvingAddresses.add(device.address)) return
        val callback = StatusReadCallback(device.address, runCatching { device.name }.getOrNull())
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context.applicationContext, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context.applicationContext, false, callback)
        }
        callback.gatt = gatt
    }

    private fun publish(endpoint: PcBluetoothEndpoint) {
        val pc = DiscoveredPc(
            serviceName = endpoint.displayName,
            desktopId = endpoint.desktopId,
            bluetoothEndpoint = endpoint
        )
        discovered[endpoint.desktopId] = pc
        _pcs.value = discovered.values.toList()
        _status.value = PcDiscoveryStatus.Found
    }

    private inner class StatusReadCallback(
        private val deviceAddress: String,
        private val deviceName: String?
    ) : BluetoothGattCallback() {
        lateinit var gatt: BluetoothGatt

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                resolvingAddresses.remove(deviceAddress)
                runCatching { gatt.close() }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                resolvingAddresses.remove(deviceAddress)
                gatt.disconnect()
                return
            }
            val characteristic = gatt.getService(PcBleConstants.serviceUuid)
                ?.getCharacteristic(PcBleConstants.statusCharacteristicUuid)
            if (characteristic == null || !gatt.readCharacteristic(characteristic)) {
                resolvingAddresses.remove(deviceAddress)
                gatt.disconnect()
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            handleRead(gatt, status, characteristic.value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleRead(gatt, status, value)
        }

        @SuppressLint("MissingPermission")
        private fun handleRead(gatt: BluetoothGatt, status: Int, value: ByteArray) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val rawStatus = String(value, StandardCharsets.UTF_8)
                PcBleStatusParser.parse(deviceAddress, deviceName, rawStatus)?.let(::publish)
            }
            resolvingAddresses.remove(deviceAddress)
            gatt.disconnect()
        }
    }
}
