package com.enaboapps.switchify.service.pcswitchforwarding

import com.enaboapps.switchify.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcSwitchForwardingLauncherTest {
    @Test
    fun startedResultAllowsActivityLaunch() {
        assertNull(pcSwitchForwardingLaunchErrorMessage(PcSwitchForwardingStartResult.Started))
    }

    @Test
    fun missingSwitchesUsesExistingWarning() {
        assertEquals(
            R.string.pc_switch_forwarding_no_external_switches,
            pcSwitchForwardingLaunchErrorMessage(PcSwitchForwardingStartResult.NoExternalSwitches)
        )
    }

    @Test
    fun unsupportedPcUsesExistingWarning() {
        assertEquals(
            R.string.pc_switch_forwarding_pc_unsupported,
            pcSwitchForwardingLaunchErrorMessage(PcSwitchForwardingStartResult.UnsupportedPc)
        )
    }
}
