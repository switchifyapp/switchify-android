package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.pc.DiscoveredPc

internal sealed class PcMainMenuSelection {
    data object NoPcFound : PcMainMenuSelection()
    data class Connect(val pc: DiscoveredPc) : PcMainMenuSelection()
    data class ShowChooser(val pcs: List<DiscoveredPc>) : PcMainMenuSelection()
}

internal fun selectPcForMainMenu(
    discovered: List<DiscoveredPc>,
    defaultDesktopId: String?
): PcMainMenuSelection {
    if (discovered.isEmpty()) return PcMainMenuSelection.NoPcFound
    val defaultPc = discovered.firstOrNull { it.desktopId == defaultDesktopId }
    if (defaultPc != null) return PcMainMenuSelection.Connect(defaultPc)
    if (discovered.size == 1) return PcMainMenuSelection.Connect(discovered.single())
    return PcMainMenuSelection.ShowChooser(discovered)
}
