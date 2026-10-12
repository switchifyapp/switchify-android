package com.enaboapps.switchify.pc.connection

import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.pc.transport.PcDiscoveredDesktop

data class PcListItem(
    val desktop: PcDiscoveredDesktop,
    val saved: PcSavedPc?,
    val nearby: Boolean
) {
    val desktopId: String get() = desktop.desktopId
    val action: PcListAction get() = if (saved != null) PcListAction.Connect else PcListAction.RequestAccess

    fun savedForConnection(): PcSavedPc? = saved?.copy(
        displayName = desktop.displayName,
        platform = desktop.platform,
        peripheralId = desktop.peripheralId
    )
}

enum class PcListAction {
    Connect,
    RequestAccess
}

object PcList {
    fun merge(saved: List<PcSavedPc>, discovered: List<PcDiscoveredDesktop>): List<PcListItem> {
        val rows = LinkedHashMap<String, PcListItem>()
        saved.forEach { pc -> rows[pc.desktopId] = PcListItem(pc.toDesktop(), pc, nearby = false) }
        discovered.forEach { desktop ->
            rows[desktop.desktopId] = PcListItem(desktop, rows[desktop.desktopId]?.saved, nearby = true)
        }
        return rows.values.toList()
    }

    fun PcSavedPc.toDesktop() = PcDiscoveredDesktop(
        desktopId = desktopId,
        displayName = displayName,
        platform = platform,
        responseTransport = null,
        peripheralId = peripheralId,
        rssi = null
    )
}
