package com.enaboapps.switchify.pc

data class DiscoveredPc(
    val serviceName: String,
    val desktopId: String,
    val hostAddresses: List<String>,
    val port: Int,
    val websocketUrls: List<String>
) {
    val displayName: String
        get() = serviceName.ifBlank { "Switchify PC" }

    val primaryAddress: String
        get() = hostAddresses.firstOrNull().orEmpty()
}
