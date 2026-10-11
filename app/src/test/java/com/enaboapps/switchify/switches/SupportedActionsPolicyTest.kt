package com.enaboapps.switchify.switches

import org.junit.Assert.*
import org.junit.Test

class SupportedActionsPolicyTest {
    @Test fun launchAndPcControlActionsAreSupportedInEveryMode() {
        listOf("auto", "manual", "unknown").forEach { mode ->
            val supported = SupportedActionsPolicy.supportedActionIdsForMode(mode)
            assertTrue(19 in supported)
            assertTrue(1 in supported)
            assertTrue(SwitchAction.ACTION_OPEN_PC_MOUSE in supported)
            assertTrue(SwitchAction.ACTION_OPEN_PC_FORWARDING in supported)
        }
        assertTrue(SwitchAction.actions.any { it.id == 17 } && SwitchAction.actions.any { it.id == 18 })
    }
}
