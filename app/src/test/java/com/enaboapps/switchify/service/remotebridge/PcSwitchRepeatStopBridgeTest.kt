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
        assertFalse(hook.requestStop())
        assertEquals(10, stops)
        second.release()
        assertFalse(hook.isArmed())
    }

    @Test fun configuredExternalSwitchStopsInAppRepeatAndIsConsumed() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        var stops = 0
        handles += PcSwitchRepeatStop.hook.arm { stops += 1 }

        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(31))
        assertEquals(0, stops)
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(30))
        assertEquals(1, stops)
        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(30))
    }

    @Test fun anySwitchStopRequestReachesInAppRepeatBeforeBridgedRepeat() {
        assertTrue(SwitchifyRemoteBridgeCoordinator.setRepeatActive(7, true))
        var stops = 0
        handles += PcSwitchRepeatStop.hook.arm { stops += 1 }
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
        assertEquals(1, stops)
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
    }

    @Test fun reportsConfiguredExternalSwitches() {
        assertFalse(SwitchifyRemoteBridgeCoordinator.hasConfiguredExternalSwitches())
        SwitchifyRemoteBridgeCoordinator.attach { emptyList() }
        assertFalse(SwitchifyRemoteBridgeCoordinator.hasConfiguredExternalSwitches())
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.hasConfiguredExternalSwitches())
    }
}
