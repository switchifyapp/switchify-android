package com.enaboapps.switchify.pc

data class DiscoveredPc(
    val serviceName: String,
    val desktopId: String,
    val bluetoothEndpoint: PcBluetoothEndpoint? = null
) {
    val displayName: String
        get() = serviceName.ifBlank { "Switchify PC" }

    val controlDeviceName: String
        get() = bluetoothEndpoint?.deviceName?.takeIf { it.isNotBlank() }
            ?: bluetoothEndpoint?.displayName?.takeIf { it.isNotBlank() }
            ?: serviceName.takeIf { it.isNotBlank() }
            ?: "Switchify PC"

    val primaryAddress: String
        get() = bluetoothEndpoint?.deviceName
            ?: bluetoothEndpoint?.deviceAddress
            ?: ""
}

data class PcBluetoothEndpoint(
    val deviceAddress: String,
    val deviceName: String?,
    val desktopId: String,
    val displayName: String
)
