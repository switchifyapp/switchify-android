package com.enaboapps.switchify.service.pcswitchcontrol

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.pc.PcProtocol
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.switches.SwitchAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PcSwitchControlCompatibilityTest {
    @Test
    @Suppress("DEPRECATION")
    fun persistedIdentifiersRemainCompatible() {
        assertEquals(
            "control_grid_3",
            MenuConstants.ItemIds.Main.PC_SWITCH_CONTROL
        )
        assertEquals(
            MenuConstants.ItemIds.Main.PC_SWITCH_CONTROL,
            MenuConstants.ItemIds.Main.CONTROL_GRID_3
        )
        assertEquals(
            "grid3_hold_to_stop_duration",
            PreferenceManager.PREFERENCE_KEY_PC_SWITCH_CONTROL_HOLD_TO_STOP_DURATION
        )
        assertEquals(
            PreferenceManager.PREFERENCE_KEY_PC_SWITCH_CONTROL_HOLD_TO_STOP_DURATION,
            PreferenceManager.PREFERENCE_KEY_GRID3_HOLD_TO_STOP_DURATION
        )
        assertEquals(18, SwitchAction.ACTION_PC_SWITCH_CONTROL)
        assertEquals(
            SwitchAction.ACTION_PC_SWITCH_CONTROL,
            SwitchAction.ACTION_CONTROL_GRID_3
        )
    }

    @Test
    fun legacyGridProtocolIdentifiersRemainCompatible() {
        assertEquals("grid.switch.set", PcProtocol.LEGACY_GRID_SWITCH_SET_COMMAND)
        assertEquals("grid.switch.sync", PcProtocol.LEGACY_GRID_SWITCH_SYNC_COMMAND)
    }
}
