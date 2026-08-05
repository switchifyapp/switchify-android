package com.enaboapps.switchify.switches

import com.enaboapps.switchify.service.scanning.ScanMode
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedActionsPolicyTest {
    @Test
    fun pcSwitchControlIsSupportedInEveryScanMode() {
        listOf(
            ScanMode.Modes.MODE_AUTO,
            ScanMode.Modes.MODE_MANUAL
        ).forEach { mode ->
            assertTrue(
                SupportedActionsPolicy.supportedActionIdsForMode(mode)
                    .contains(SwitchAction.ACTION_PC_SWITCH_CONTROL)
            )
        }
    }

    @Test
    fun unknownModeUsesAutoScanActions() {
        assertTrue(
            SupportedActionsPolicy.supportedActionIdsForMode("unknown")
                .contains(SwitchAction.ACTION_SELECT)
        )
    }
}
