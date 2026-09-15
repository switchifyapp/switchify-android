package com.enaboapps.switchify.switches

import org.junit.Assert.*
import org.junit.Test

class SupportedActionsPolicyTest {
    @Test fun launchReplacesRetiredActionsInEveryMode() {
        listOf("auto", "manual", "unknown").forEach { mode ->
            val supported = SupportedActionsPolicy.supportedActionIdsForMode(mode)
            assertTrue(19 in supported)
            assertTrue(1 in supported)
            assertFalse(17 in supported)
            assertFalse(18 in supported)
        }
        assertFalse(SwitchAction.actions.any { it.id == 17 || it.id == 18 })
    }
}
