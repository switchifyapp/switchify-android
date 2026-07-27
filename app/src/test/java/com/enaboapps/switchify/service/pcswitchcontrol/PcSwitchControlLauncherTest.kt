package com.enaboapps.switchify.service.pcswitchcontrol

import com.enaboapps.switchify.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcSwitchControlLauncherTest {
    @Test
    fun startedResultAllowsActivityLaunch() {
        assertNull(pcSwitchControlLaunchErrorMessage(PcSwitchControlStartResult.Started))
    }

    @Test
    fun missingSwitchesUsesExistingWarning() {
        assertEquals(
            R.string.pc_switch_control_no_external_switches,
            pcSwitchControlLaunchErrorMessage(PcSwitchControlStartResult.NoExternalSwitches)
        )
    }

    @Test
    fun unsupportedPcUsesExistingWarning() {
        assertEquals(
            R.string.pc_switch_control_pc_unsupported,
            pcSwitchControlLaunchErrorMessage(PcSwitchControlStartResult.UnsupportedPc)
        )
    }
}
