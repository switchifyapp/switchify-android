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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

private const val STATUS_READ_TIMEOUT_MS = 5_000L

class PcBleDiscoveryService(
    private val context: Context,
    private val bluetoothManagerProvider: () -> BluetoothManager? = {
        (context.applicationContext ?: context).getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
) : PcDiscovery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val discovered = linkedMapOf<String, DiscoveredPc>()
    private val resolvingAddresses = mutableSetOf<String>()
    private val resolvingGatts = mutableMapOf<String, BluetoothGatt>()
    private val resolvingLock = Any()
    private var scanCallback: ScanCallback? = null
    private var discoveryGeneration = 0L

    private val _pcs = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    override val pcs: StateFlow<List<DiscoveredPc>> = _pcs

    private val _status = MutableStateFlow(PcDiscoveryStatus.Idle)
    override val status: StateFlow<PcDiscoveryStatus> = _status

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        stopDiscovery()
        val generation = synchronized(resolvingLock) {
            discoveryGeneration += 1
            discoveryGeneration
        }
        discovered.clear()
        synchronized(resolvingLock) {
            resolvingAddresses.clear()
            resolvingGatts.clear()
        }
        _pcs.value = emptyList()
        _status.value = PcDiscoveryStatus.Searching

        if (!context.hasBluetoothScanPermission() || !context.hasBluetoothConnectPermission()) {
            _status.value = PcDiscoveryStatus.Failed
            return
        }
        val scanner = bluetoothManagerProvider()?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            _status.value = PcDiscoveryStatus.Failed
            return
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch { resolve(result, generation) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> scope.launch { resolve(result, generation) } }
            }

            override fun onScanFailed(errorCode: Int) {
                if (isActiveGeneration(generation)) {
                    _status.value = PcDiscoveryStatus.Failed
                }
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
        val callback = scanCallback
        val scanner = bluetoothManagerProvider()?.adapter?.bluetoothLeScanner
        if (callback != null) {
            runCatching { scanner?.stopScan(callback) }
        }
        scanCallback = null
        val gatts = synchronized(resolvingLock) {
            discoveryGeneration += 1
            resolvingAddresses.clear()
            resolvingGatts.values.toList().also { resolvingGatts.clear() }
        }
        gatts.forEach { gatt ->
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        if (_pcs.value.isEmpty() && _status.value == PcDiscoveryStatus.Searching) {
            _status.value = PcDiscoveryStatus.Empty
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolve(result: ScanResult, generation: Long) {
        if (!context.hasBluetoothConnectPermission()) return
        val device = result.device ?: return
        val shouldResolve = synchronized(resolvingLock) {
            if (generation != discoveryGeneration) return
            resolvingAddresses.add(device.address)
        }
        if (!shouldResolve) return
        val callback = StatusReadCallback(generation, device.address, runCatching { device.name }.getOrNull())
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext(), false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(appContext(), false, callback)
        }
        synchronized(resolvingLock) {
            resolvingGatts[device.address] = gatt
        }
        callback.gatt = gatt
        callback.scheduleTimeout()
    }

    private fun publish(endpoint: PcBluetoothEndpoint, generation: Long) {
        val pcs = synchronized(resolvingLock) {
            if (generation != discoveryGeneration || endpoint.deviceAddress !in resolvingAddresses) return
            val pc = DiscoveredPc(
                serviceName = endpoint.displayName,
                desktopId = endpoint.desktopId,
                bluetoothEndpoint = endpoint
            )
            discovered[endpoint.desktopId] = pc
            discovered.values.toList()
        }
        _pcs.value = pcs
        _status.value = PcDiscoveryStatus.Found
    }

    private fun isActiveGeneration(generation: Long): Boolean {
        return synchronized(resolvingLock) { generation == discoveryGeneration }
    }

    private fun appContext(): Context {
        return context.applicationContext ?: context
    }

    private inner class StatusReadCallback(
        private val generation: Long,
        private val deviceAddress: String,
        private val deviceName: String?
    ) : BluetoothGattCallback() {
        lateinit var gatt: BluetoothGatt

        fun scheduleTimeout() {
            scope.launch {
                delay(STATUS_READ_TIMEOUT_MS)
                finishIfStillResolving()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                finishGatt(gatt, disconnect = false)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                finishGatt(gatt)
                return
            }
            val characteristic = gatt.getService(PcBleConstants.serviceUuid)
                ?.getCharacteristic(PcBleConstants.statusCharacteristicUuid)
            if (characteristic == null || !gatt.readCharacteristic(characteristic)) {
                finishGatt(gatt)
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
                PcBleStatusParser.parse(deviceAddress, deviceName, rawStatus)?.let { publish(it, generation) }
            }
            finishGatt(gatt)
        }

        @SuppressLint("MissingPermission")
        private fun finishGatt(gatt: BluetoothGatt, disconnect: Boolean = true) {
            synchronized(resolvingLock) {
                resolvingAddresses.remove(deviceAddress)
                resolvingGatts.remove(deviceAddress)
            }
            if (disconnect) runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }

        @SuppressLint("MissingPermission")
        private fun finishIfStillResolving() {
            val pendingGatt = synchronized(resolvingLock) {
                if (!resolvingAddresses.remove(deviceAddress)) {
                    null
                } else {
                    resolvingGatts.remove(deviceAddress)
                }
            }
            pendingGatt?.let { gatt ->
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
            }
        }
    }
}
