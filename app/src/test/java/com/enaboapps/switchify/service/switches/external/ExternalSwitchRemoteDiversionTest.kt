package com.enaboapps.switchify.service.switches.external

import com.enaboapps.switchify.service.remotebridge.SwitchifyRemoteBridgeCoordinator
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSwitchRemoteDiversionTest {
    @After
    fun cleanup() {
        SwitchifyRemoteBridgeCoordinator.detach()
        SwitchifyRemoteBridgeCoordinator.resetForTests()
    }

    @Test
    fun unconfiguredKeyDoesNotStopRemoteRepeat() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setRepeatActive(10, true))
        val diversion = ExternalSwitchRemoteDiversion()

        assertFalse(diversion.onPressed(31, 1, 1) { false })
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
    }

    @Test
    fun releaseOfNormallyHandledPressIsSuppressedWhenForwardingActivates() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        val diversion = ExternalSwitchRemoteDiversion()
        assertTrue(diversion.onPressed(30, 1, 1) { true })
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(10, true))
        var suppressed = false
        var handledNormally = false

        assertTrue(
            diversion.onReleased(
                keyCode = 30,
                downTimeMs = 1,
                eventTimeMs = 2,
                cancelled = false,
                suppressedReleaseHandling = { suppressed = true; true },
                normalHandling = { handledNormally = true; true }
            )
        )
        assertTrue(suppressed)
        assertFalse(handledNormally)
    }
}
