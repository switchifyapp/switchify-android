package com.enaboapps.switchify.service.remotebridge

import com.enaboapps.switchify.pc.remote.PcSwitchRepeatStop
import com.enaboapps.switchify.pc.remote.PcSwitchRepeatStopHook
import com.enaboapps.switchify.pc.remote.PcSwitchStopHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PcSwitchRepeatStopBridgeTest {
    private val handles = mutableListOf<PcSwitchStopHandle>()

    @Before fun setUp() {
        SwitchifyRemoteBridgeCoordinator.setScanningPaused = {}
    }

    @After fun cleanup() {
        handles.forEach { it.release() }
        SwitchifyRemoteBridgeCoordinator.detach()
        SwitchifyRemoteBridgeCoordinator.resetForTests()
    }

    @Test fun hookStopsOnceAndIgnoresStaleRelease() {
        val hook = PcSwitchRepeatStopHook()
        var stops = 0
        assertFalse(hook.requestStop())
        val first = hook.arm { stops += 1 }
        val second = hook.arm { stops += 10 }
        first.release()
        assertTrue(hook.isArmed())
        assertTrue(hook.requestStop())
        assertTrue(hook.requestStop())
        assertEquals(10, stops)
        assertTrue(hook.isArmed())
        second.release()
        assertFalse(hook.isArmed())
        assertFalse(hook.requestStop())
    }

    @Test fun configuredExternalSwitchStopsInAppRepeatAndIsConsumed() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        var stops = 0
        val handle = PcSwitchRepeatStop.hook.arm { stops += 1 }
        handles += handle

        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(31))
        assertEquals(0, stops)
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(30))
        assertEquals(1, stops)
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(30))
        assertEquals(1, stops)
        handle.release()
        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(30))
    }

    @Test fun anySwitchStopRequestReachesInAppRepeatBeforeBridgedRepeat() {
        assertTrue(SwitchifyRemoteBridgeCoordinator.setRepeatActive(7, true))
        var stops = 0
        val handle = PcSwitchRepeatStop.hook.arm { stops += 1 }
        handles += handle
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
        assertEquals(1, stops)
        handle.release()
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
    }

    @Test fun hookReportsArmedChangesForEveryEndPath() {
        val changes = mutableListOf<Boolean>()
        val hook = PcSwitchRepeatStopHook(onArmedChanged = { changes += it })
        val first = hook.arm {}
        first.release()
        first.release()
        val stopped = hook.arm {}
        hook.requestStop()
        assertEquals(listOf(true, false, true), changes)
        stopped.release()
        val replaced = hook.arm {}
        val replacement = hook.arm {}
        replaced.release()
        replacement.release()
        assertEquals(listOf(true, false, true, false, true, false), changes)
    }

    @Test fun cameraSwitchesCountAsConfigured() {
        SwitchifyRemoteBridgeCoordinator.attach({ true }) { emptyList() }
        assertTrue(SwitchifyRemoteBridgeCoordinator.hasConfiguredSwitches())
        SwitchifyRemoteBridgeCoordinator.detach()
        assertFalse(SwitchifyRemoteBridgeCoordinator.hasConfiguredSwitches())
    }

    @Test fun reportsConfiguredExternalSwitches() {
        assertFalse(SwitchifyRemoteBridgeCoordinator.hasConfiguredSwitches())
        SwitchifyRemoteBridgeCoordinator.attach { emptyList() }
        assertFalse(SwitchifyRemoteBridgeCoordinator.hasConfiguredSwitches())
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.hasConfiguredSwitches())
    }
}
