package com.enaboapps.switchify.service.techniques.pointscan.blocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PointScanGridVisibilityTest {
    @Test
    fun deferredNodeSelectionRetainsGridDespitePreviouslyQueuedStopCallbacks() {
        val fixture = Fixture()
        fixture.visibility.show()
        fixture.drain()
        val original = fixture.grid
        fixture.queue.addLast {
            fixture.visibility.holdForLinePhase()
            fixture.visibility.hide()
            fixture.visibility.hide()
        }
        fixture.visibility.hide()
        fixture.visibility.hide()
        fixture.drain()

        assertEquals(original, fixture.grid)
        assertEquals(PointScanGridPhase.LINE, fixture.phase)
        assertEquals(0, fixture.removals)
    }

    @Test
    fun liveStructureRefreshKeepsReplacementVisibleAfterAllCallbacks() {
        val fixture = Fixture()
        fixture.visibility.show()
        fixture.drain()
        fixture.visibility.hide()
        fixture.visibility.reset()
        fixture.visibility.hide()
        fixture.visibility.show()
        fixture.drain()

        assertNotNull(fixture.grid)
        assertEquals(PointScanGridPhase.SCANNING, fixture.phase)
        fixture.visibility.show()
        fixture.drain()
        assertNotNull(fixture.grid)
    }

    @Test
    fun restartAfterCompletedRemovalAttachesANewGrid() {
        val fixture = Fixture()
        fixture.visibility.show()
        fixture.drain()
        fixture.visibility.reset()
        fixture.drain()
        assertNull(fixture.grid)
        fixture.visibility.show()
        fixture.drain()

        assertEquals(2, fixture.attachments)
        assertEquals(1, fixture.removals)
        assertNotNull(fixture.grid)
    }

    @Test
    fun finalResetOverridesQueuedLinePhaseAndRemovesGrid() {
        val fixture = Fixture()
        fixture.visibility.show()
        fixture.drain()
        fixture.visibility.holdForLinePhase()
        fixture.visibility.hide()
        fixture.visibility.reset()
        fixture.drain()

        assertNull(fixture.grid)
        assertEquals(PointScanGridPhase.HIDDEN, fixture.phase)
        assertEquals(1, fixture.removals)
    }

    @Test
    fun resetPreventsPendingInitialShowFromAttachingAfterCleanup() {
        val fixture = Fixture()
        fixture.visibility.show()
        fixture.visibility.reset()
        fixture.drain()

        assertNull(fixture.grid)
        assertEquals(0, fixture.attachments)
    }

    @Test
    fun cycleCompletionWithoutSelectionRemovesGrid() {
        val fixture = Fixture()
        fixture.visibility.show()
        fixture.drain()
        fixture.visibility.hide()
        fixture.visibility.hide()
        fixture.drain()

        assertNull(fixture.grid)
        assertEquals(1, fixture.removals)
    }

    @Test
    fun nextBlockScanRestoresFullGridAfterLinePhase() {
        val fixture = Fixture()
        fixture.visibility.show()
        fixture.drain()
        fixture.visibility.holdForLinePhase()
        fixture.visibility.hide()
        fixture.drain()
        val original = fixture.grid
        fixture.visibility.show()
        fixture.drain()

        assertEquals(original, fixture.grid)
        assertEquals(PointScanGridPhase.SCANNING, fixture.phase)
    }

    private class Fixture {
        val queue = ArrayDeque<() -> Unit>()
        var grid: Int? = null
        var phase = PointScanGridPhase.HIDDEN
        var attachments = 0
        var removals = 0
        val visibility = PointScanGridVisibility(queue::addLast) { next ->
            phase = next
            if (next == PointScanGridPhase.HIDDEN) {
                if (grid != null) removals++
                grid = null
            } else if (grid == null) {
                grid = ++attachments
            }
        }

        fun drain() {
            while (queue.isNotEmpty()) queue.removeFirst().invoke()
        }
    }
}
