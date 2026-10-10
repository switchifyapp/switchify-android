package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.pc.remote.PcSwitchRepeatStop
import com.enaboapps.switchify.pc.remote.PcSwitchStopHandle
import com.enaboapps.switchify.service.remotebridge.SwitchifyRemoteBridgeCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TasksPcRepeatTest {
    private val handles = mutableListOf<PcSwitchStopHandle>()

    @Before fun setUp() {
        SwitchifyRemoteBridgeCoordinator.resetForTests()
        SwitchifyRemoteBridgeCoordinator.setScanningPaused = {}
    }

    @After fun cleanup() {
        handles.forEach { it.release() }
        SwitchifyRemoteBridgeCoordinator.detach()
        SwitchifyRemoteBridgeCoordinator.resetForTests()
    }

    @Test fun noStoppableTaskWithoutPcRepeat() {
        assertFalse(Tasks.getInstance().hasActiveStoppableTask())
        assertFalse(Tasks.getInstance().stopActiveStoppableTask())
    }

    @Test fun armedPcRepeatIsAStoppableTaskUntilReleased() {
        val handle = PcSwitchRepeatStop.hook.arm {}
        handles += handle
        assertTrue(Tasks.getInstance().hasActiveStoppableTask())
        handle.release()
        assertFalse(Tasks.getInstance().hasActiveStoppableTask())
    }

    @Test fun switchPressStopsPcRepeatOnceAndIsConsumedWhileItWindsDown() {
        var stops = 0
        val handle = PcSwitchRepeatStop.hook.arm { stops += 1 }
        handles += handle
        assertTrue(Tasks.getInstance().stopActiveStoppableTask())
        assertTrue(Tasks.getInstance().stopOngoingTaskForSwitchPress())
        assertEquals(1, stops)
        handle.release()
        assertFalse(Tasks.getInstance().stopActiveStoppableTask())
        assertEquals(1, stops)
    }

    @Test fun bridgedRepeatIsStoppedAfterPcRepeat() {
        assertTrue(SwitchifyRemoteBridgeCoordinator.setRepeatActive(11, true))
        var stops = 0
        val handle = PcSwitchRepeatStop.hook.arm { stops += 1 }
        handles += handle
        assertTrue(Tasks.getInstance().stopActiveStoppableTask())
        assertEquals(1, stops)
        handle.release()
        assertTrue(Tasks.getInstance().stopActiveStoppableTask())
        assertFalse(Tasks.getInstance().stopActiveStoppableTask())
    }
}
