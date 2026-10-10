package com.enaboapps.switchify.service.remotebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanningHoldsTest {
    @Test fun scanningResumesOnlyWhenEveryOwnerReleases() {
        val holds = ScanningHolds()
        assertEquals(true, holds.update(ScanningHold.Forwarding, true))
        assertNull(holds.update(ScanningHold.PcRepeat, true))
        assertNull(holds.update(ScanningHold.PcRepeat, false))
        assertTrue(holds.isHeld())
        assertEquals(false, holds.update(ScanningHold.Forwarding, false))
        assertFalse(holds.isHeld())
    }

    @Test fun releasingAnOwnerThatNeverHeldDoesNotResume() {
        val holds = ScanningHolds()
        assertEquals(true, holds.update(ScanningHold.Forwarding, true))
        assertNull(holds.update(ScanningHold.PcRepeat, false))
        assertTrue(holds.isHeld())
    }
}
