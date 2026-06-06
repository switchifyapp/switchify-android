package com.enaboapps.switchify.pc

data class PcServiceRecord(
    val serviceName: String,
    val attributes: Map<String, String>,
    val hostAddresses: List<String>,
    val port: Int
)

object PcDiscoveryParser {
    fun parse(record: PcServiceRecord): DiscoveredPc? {
        if (record.attributes[PcTxtKeys.KIND] != "switchify.pc") return null
        if (record.attributes[PcTxtKeys.VERSION] != "1") return null
        if (record.attributes[PcTxtKeys.PROTOCOL_VERSION] != "1") return null
        val desktopId = record.attributes[PcTxtKeys.DESKTOP_ID]?.takeIf { it.isNotBlank() } ?: return null
        if (record.port <= 0) return null
        val orderedAddresses = PcAddressSelector.orderAddresses(record.hostAddresses)
        if (orderedAddresses.isEmpty()) return null
        return DiscoveredPc(
            serviceName = record.serviceName,
            desktopId = desktopId,
            hostAddresses = orderedAddresses,
            port = record.port,
            websocketUrls = PcAddressSelector.websocketUrls(orderedAddresses, record.port)
        )
    }
}
