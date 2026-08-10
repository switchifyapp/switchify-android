package com.enaboapps.switchify.pc

data class DiscoveredPc(
    val serviceName: String,
    val desktopId: String,
    val bluetoothEndpoint: PcBluetoothEndpoint? = null
) {
    val displayName: String
        get() = serviceName.ifBlank { "Switchify PC" }

    val controlDeviceName: String
        get() {
            val endpoint = bluetoothEndpoint
            return if (endpoint?.platform == PcPlatform.MacOS) {
                endpoint.displayName.trim().takeIf { it.isNotEmpty() }
                    ?: serviceName.trim().takeIf { it.isNotEmpty() }
                    ?: "Switchify PC"
            } else {
                endpoint?.deviceName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: endpoint?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: serviceName.trim().takeIf { it.isNotEmpty() }
                    ?: "Switchify PC"
            }
        }

    val primaryAddress: String
        get() = bluetoothEndpoint?.let { endpoint ->
            if (endpoint.platform == PcPlatform.MacOS) {
                endpoint.deviceAddress
            } else {
                endpoint.deviceName?.trim()?.takeIf { it.isNotEmpty() } ?: endpoint.deviceAddress
            }
        } ?: ""
}

data class PcBluetoothEndpoint(
    val deviceAddress: String,
    val deviceName: String?,
    val desktopId: String,
    val displayName: String,
    val platform: PcPlatform? = null
)

enum class PcPlatform(val wireValue: String, val displayName: String) {
    Windows("windows", "Windows"),
    MacOS("macos", "macOS")
}
