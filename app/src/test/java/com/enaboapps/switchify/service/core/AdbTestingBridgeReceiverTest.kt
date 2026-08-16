package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbTestingBridgeReceiverTest {
    @Test
    fun pcSwitchForwardingUsesStableSwitchAction() {
        assertEquals(
            SwitchAction.ACTION_PC_SWITCH_FORWARDING,
            AdbTestingBridgeReceiver.actionNameToId["pc_switch_forwarding"]
        )
    }

    @Test
    fun pcSwitchForwardingMenuUsesForwardingIdentifier() {
        assertEquals("pc_switch_forwarding", MenuConstants.ItemIds.Main.PC_SWITCH_FORWARDING)
    }
}
