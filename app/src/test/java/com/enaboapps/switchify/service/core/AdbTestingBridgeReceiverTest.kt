package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.switches.SwitchAction
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbTestingBridgeReceiverTest {
    @Test
    fun pcSwitchControlAliasesUseStableSwitchAction() {
        assertEquals(
            SwitchAction.ACTION_PC_SWITCH_CONTROL,
            AdbTestingBridgeReceiver.actionNameToId["control_grid_3"]
        )
        assertEquals(
            SwitchAction.ACTION_PC_SWITCH_CONTROL,
            AdbTestingBridgeReceiver.actionNameToId["pc_switch_control"]
        )
    }
}
