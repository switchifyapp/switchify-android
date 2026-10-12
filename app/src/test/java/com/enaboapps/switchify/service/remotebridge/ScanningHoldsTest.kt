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

    @Test fun applyPausesOnFirstHoldAndResumesOnLastRelease() {
        val holds = ScanningHolds()
        val target = FakeScanningHoldTarget()
        holds.apply(ScanningHold.Forwarding, true, target)
        holds.apply(ScanningHold.PcRepeat, true, target)
        holds.apply(ScanningHold.Forwarding, false, target)
        holds.apply(ScanningHold.PcRepeat, false, target)
        assertEquals(listOf("pause", "resume"), target.calls)
    }

    @Test fun applyLeavesTheUsersPauseInPlace() {
        val holds = ScanningHolds()
        val target = FakeScanningHoldTarget()
        holds.apply(ScanningHold.PcRepeat, true, target)
        target.pausedByUser = true
        holds.apply(ScanningHold.PcRepeat, false, target)
        assertEquals(listOf("pause"), target.calls)
        assertFalse(holds.isHeld())
    }

    @Test fun resetDropsEveryHold() {
        val holds = ScanningHolds()
        holds.update(ScanningHold.Forwarding, true)
        holds.update(ScanningHold.PcRepeat, true)
        holds.reset()
        assertFalse(holds.isHeld())
        assertEquals(true, holds.update(ScanningHold.PcRepeat, true))
    }
}
