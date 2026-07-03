package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcDefaultPcPreference

internal sealed class PcMainMenuSelection {
    data object NoPcFound : PcMainMenuSelection()
    data class Connect(val pc: DiscoveredPc) : PcMainMenuSelection()
    data class ShowChooser(val pcs: List<DiscoveredPc>) : PcMainMenuSelection()
}

internal fun selectPcForMainMenu(
    discovered: List<DiscoveredPc>,
    defaultPreference: PcDefaultPcPreference,
    lastConnectedDesktopId: String?
): PcMainMenuSelection {
    if (discovered.isEmpty()) return PcMainMenuSelection.NoPcFound
    val preferredDesktopId = when (defaultPreference) {
        PcDefaultPcPreference.LastConnection -> lastConnectedDesktopId
        is PcDefaultPcPreference.SpecificPc -> defaultPreference.desktopId
    }
    val preferredPc = discovered.firstOrNull { it.desktopId == preferredDesktopId }
    if (preferredPc != null) return PcMainMenuSelection.Connect(preferredPc)
    if (discovered.size == 1) return PcMainMenuSelection.Connect(discovered.single())
    return PcMainMenuSelection.ShowChooser(discovered)
}
