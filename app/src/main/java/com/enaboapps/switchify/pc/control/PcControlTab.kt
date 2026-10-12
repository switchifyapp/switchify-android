package com.enaboapps.switchify.pc.control

import com.enaboapps.switchify.pc.storage.PcSavedPc

enum class PcControlTab {
    Pcs,
    Remote,
    Settings
}

object PcControlOpening {
    fun initialTab(saved: List<PcSavedPc>): PcControlTab =
        if (saved.isEmpty()) PcControlTab.Pcs else PcControlTab.Remote

    fun tabAfterSetup(): PcControlTab = PcControlTab.Pcs
}
