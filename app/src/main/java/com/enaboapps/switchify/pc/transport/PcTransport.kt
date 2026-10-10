package com.enaboapps.switchify.pc.transport

import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.protocol.PcResponseTransport
import com.enaboapps.switchify.pc.protocol.PcStatus

fun interface PcUnsubscribe {
    fun unsubscribe()
}

enum class PcBluetoothAvailability {
    Ready,
    Unauthorized,
    PoweredOff,
    Unsupported
}

data class PcDiscoveredDesktop(
    val desktopId: String,
    val displayName: String,
    val platform: PcPlatform?,
    val responseTransport: PcResponseTransport?,
    val peripheralId: String,
    val rssi: Int?
)

open class PcBluetoothException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface PcTransport {
    suspend fun availability(): PcBluetoothAvailability
    fun scan(onDesktop: (PcDiscoveredDesktop) -> Unit, onError: (Throwable) -> Unit): PcUnsubscribe
    suspend fun connect(peripheralId: String)
    suspend fun resolveAndConnect(desktopId: String): PcDiscoveredDesktop
    suspend fun disconnect()
    fun maxWriteValueBytes(): Int
    suspend fun writeFrame(frame: ByteArray)
    suspend fun cancelPendingWrites()
    suspend fun verifyConnection(desktopId: String): Boolean
    fun subscribe(onFrame: (ByteArray) -> Unit, onError: (Throwable) -> Unit): PcUnsubscribe
    suspend fun notificationsReady()
    fun subscribeDisconnect(onDisconnect: () -> Unit): PcUnsubscribe
}

object PcDesktopDisplayName {
    fun bluetoothDeviceDisplayName(name: String?, localName: String?): String? {
        val candidates = listOfNotNull(normalized(name), normalized(localName))
        return candidates.firstOrNull { !isGeneric(it) } ?: candidates.firstOrNull()
    }

    fun desktopDisplayName(status: PcStatus, name: String?, localName: String?): String {
        val statusName = normalized(status.displayName)
        if (status.platform == PcPlatform.MacOs) return statusName ?: PRODUCT_NAME
        val candidates = listOfNotNull(bluetoothDeviceDisplayName(name, localName), statusName)
        return candidates.firstOrNull { !isGeneric(it) } ?: candidates.firstOrNull() ?: PRODUCT_NAME
    }

    private fun normalized(value: String?): String? = value?.trim()?.ifEmpty { null }

    private fun isGeneric(value: String): Boolean = value.equals(PRODUCT_NAME, ignoreCase = true)

    private const val PRODUCT_NAME = "Switchify PC"
}
