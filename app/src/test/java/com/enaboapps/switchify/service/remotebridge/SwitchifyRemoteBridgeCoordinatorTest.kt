package com.enaboapps.switchify.service.remotebridge

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchifyRemoteBridgeCoordinatorTest {
    @After fun cleanup() { SwitchifyRemoteBridgeCoordinator.detach() }

    @Test fun repeatStopIsGenerationScopedAndConsumedOnce() {
        assertTrue(SwitchifyRemoteBridgeCoordinator.setRepeatActive(7, true))
        assertFalse(SwitchifyRemoteBridgeCoordinator.setRepeatActive(6, true))
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
    }

    @Test fun forwardingSendsOnlyConfiguredOrderedEdgesAndFailsClosedAfterDetach() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(11, true))
        assertFalse(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(31, true, 1, 1, false))
        assertTrue(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, true, 2, 2, false))
        assertTrue(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, false, 2, 10, false))
        assertEquals(2L, SwitchifyRemoteBridgeCoordinator.currentEdgeSequence())
        SwitchifyRemoteBridgeCoordinator.detach()
        assertFalse(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, true, 3, 3, false))
    }

    @Test fun explicitCleanupClearsActiveState() {
        SwitchifyRemoteBridgeCoordinator.setRepeatActive(3, true)
        SwitchifyRemoteBridgeCoordinator.clearActive()
        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
    }
}
