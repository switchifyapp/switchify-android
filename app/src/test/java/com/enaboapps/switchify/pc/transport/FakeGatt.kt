package com.enaboapps.switchify.pc.transport

import com.enaboapps.switchify.pc.protocol.PcBleUuids
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

class FakeGattConnection(
    override val peripheralId: String,
    var status: ByteArray?,
    private val log: MutableList<String>,
    private val negotiatedMtu: Int? = 517
) : PcGattConnection {
    override var mtu: Int? = null
    var connected = true
    var disconnected = false
    var cccd: ByteArray? = byteArrayOf(0x01, 0x00)
    var mtuGate: CompletableDeferred<Unit>? = null
    var writeGate: CompletableDeferred<Unit>? = null
    var writeError: Exception? = null
    val writes = mutableListOf<ByteArray>()
    val responseReads = ArrayDeque<ByteArray?>()
    var responseReadCount = 0
    var monitors = 0
    var notificationListener: ((ByteArray) -> Unit)? = null
    var notificationErrorListener: ((Throwable) -> Unit)? = null
    var disconnectListener: (() -> Unit)? = null
    var inFlight = 0
    var maxInFlight = 0

    override fun isConnected() = connected

    override suspend fun requestHighPriority() {
        log += "priority:$peripheralId"
    }

    override suspend fun requestMtu(mtu: Int): Int {
        log += "mtu:$peripheralId:$mtu"
        mtuGate?.await()
        this.mtu = negotiatedMtu
        return negotiatedMtu ?: 23
    }

    override suspend fun discoverServices() {
        log += "services:$peripheralId"
    }

    override suspend fun read(characteristic: UUID): ByteArray? = when (characteristic) {
        PcBleUuids.status -> {
            log += "status:$peripheralId"
            status
        }
        PcBleUuids.response -> {
            responseReadCount += 1
            if (responseReads.isEmpty()) ByteArray(0) else responseReads.removeFirst()
        }
        else -> throw PcBluetoothException("Bluetooth characteristic is unavailable.")
    }

    override suspend fun write(characteristic: UUID, value: ByteArray) {
        check(characteristic == PcBleUuids.receive)
        inFlight += 1
        maxInFlight = maxOf(maxInFlight, inFlight)
        try {
            writeGate?.await()
            writeError?.let { throw it }
            writes += value
        } finally {
            inFlight -= 1
        }
    }

    override suspend fun readDescriptor(characteristic: UUID, descriptor: UUID): ByteArray? {
        check(characteristic == PcBleUuids.transmit && descriptor == PcBleUuids.clientCharacteristicConfiguration)
        log += "cccd:$peripheralId"
        return cccd
    }

    override fun monitor(characteristic: UUID, onValue: (ByteArray) -> Unit, onError: (Throwable) -> Unit): PcUnsubscribe {
        check(characteristic == PcBleUuids.transmit)
        monitors += 1
        notificationListener = onValue
        notificationErrorListener = onError
        return PcUnsubscribe {
            notificationListener = null
            notificationErrorListener = null
        }
    }

    override fun onDisconnected(listener: () -> Unit): PcUnsubscribe {
        disconnectListener = listener
        return PcUnsubscribe { disconnectListener = null }
    }

    override suspend fun disconnect() {
        log += "disconnect:$peripheralId"
        disconnected = true
        connected = false
    }
}

class FakeGattAdapter : PcGattAdapter {
    val log = mutableListOf<String>()
    var state = PcGattAdapterState.PoweredOn
    val statuses = mutableMapOf<String, ByteArray?>()
    val connections = mutableMapOf<String, MutableList<FakeGattConnection>>()
    val connectGates = mutableMapOf<String, CompletableDeferred<Unit>>()
    var negotiatedMtu: Int? = 517
    var mtuGate: CompletableDeferred<Unit>? = null
    var advertisementListener: ((PcGattAdvertisement) -> Unit)? = null
    var scanErrorListener: ((Throwable) -> Unit)? = null
    var scanning = false
    var activeConnects = 0
    var maxActiveConnects = 0

    override fun state() = state

    override fun startScan(serviceUuid: UUID, onAdvertisement: (PcGattAdvertisement) -> Unit, onError: (Throwable) -> Unit) {
        check(serviceUuid == PcBleUuids.service)
        log += "scan"
        scanning = true
        advertisementListener = onAdvertisement
        scanErrorListener = onError
    }

    override fun stopScan() {
        if (scanning) log += "stopScan"
        scanning = false
        advertisementListener = null
    }

    override suspend fun connect(peripheralId: String): PcGattConnection {
        log += "connect:$peripheralId"
        activeConnects += 1
        maxActiveConnects = maxOf(maxActiveConnects, activeConnects)
        try {
            connectGates[peripheralId]?.await()
        } finally {
            activeConnects -= 1
        }
        val connection = FakeGattConnection(peripheralId, statuses[peripheralId], log, negotiatedMtu).also { it.mtuGate = mtuGate }
        connections.getOrPut(peripheralId) { mutableListOf() } += connection
        return connection
    }

    fun advertise(peripheralId: String, name: String? = null, localName: String? = null) {
        advertisementListener?.invoke(PcGattAdvertisement(peripheralId, name, localName, -50))
    }

    fun latest(peripheralId: String): FakeGattConnection = connections.getValue(peripheralId).last()

    companion object {
        fun status(desktopId: String, platform: String = "windows", extra: String = ""): ByteArray =
            "{\"protocolVersion\":1,\"desktopId\":\"$desktopId\",\"displayName\":\"Switchify PC\",\"platform\":\"$platform\"$extra}"
                .toByteArray()
    }
}
