package com.enaboapps.switchify.service.face.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceStateManagerWinkTest {
    private val manager = FaceStateManager()
    private val closed = FaceStateManager.BLINK_ENTER_THRESHOLD + 0.1f
    private val open = FaceStateManager.BLINK_EXIT_THRESHOLD - 0.1f

    @Test
    fun leftWinkStaysReportedWhileHeld() {
        assertEquals(Pair(true, false), manager.processWinkDetection(closed, open, 0L))
        assertEquals(Pair(true, false), manager.processWinkDetection(closed, open, 67L))
        assertEquals(Pair(true, false), manager.processWinkDetection(closed, open, 400L))
        assertEquals(Pair(false, false), manager.processWinkDetection(open, open, 467L))
    }

    @Test
    fun rightWinkStaysReportedWhileHeld() {
        assertEquals(Pair(false, true), manager.processWinkDetection(open, closed, 0L))
        assertEquals(Pair(false, true), manager.processWinkDetection(open, closed, 1500L))
        assertEquals(Pair(false, false), manager.processWinkDetection(open, open, 1567L))
    }

    @Test
    fun bothEyesClosedIsNotAWink() {
        val (left, right) = manager.processWinkDetection(closed, closed, 0L)
        assertFalse(left)
        assertFalse(right)
    }

    @Test
    fun winkEndsWhenOtherEyeCloses() {
        assertTrue(manager.processWinkDetection(closed, open, 0L).first)
        assertFalse(manager.processWinkDetection(closed, closed, 100L).first)
    }

    @Test
    fun resetClearsHeldWink() {
        assertTrue(manager.processWinkDetection(closed, open, 0L).first)
        manager.reset()
        assertTrue(manager.processWinkDetection(closed, open, 100L).first)
    }
}
