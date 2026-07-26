package com.enaboapps.switchify.service.grid3

import com.enaboapps.switchify.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Grid3ControlLauncherTest {
    @Test
    fun startedResultAllowsActivityLaunch() {
        assertNull(grid3LaunchErrorMessage(Grid3StartResult.Started))
    }

    @Test
    fun missingSwitchesUsesExistingWarning() {
        assertEquals(
            R.string.grid3_no_external_switches,
            grid3LaunchErrorMessage(Grid3StartResult.NoExternalSwitches)
        )
    }

    @Test
    fun unsupportedPcUsesExistingWarning() {
        assertEquals(
            R.string.grid3_pc_unsupported,
            grid3LaunchErrorMessage(Grid3StartResult.UnsupportedPc)
        )
    }
}
