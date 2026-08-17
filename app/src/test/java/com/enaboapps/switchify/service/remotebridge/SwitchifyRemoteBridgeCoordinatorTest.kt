package com.enaboapps.switchify.service.remotebridge

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchifyRemoteBridgeCoordinatorTest {
    @After fun cleanup() {
        SwitchifyRemoteBridgeCoordinator.detach()
        SwitchifyRemoteBridgeCoordinator.resetForTests()
    }

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

    @Test fun delayedRepeatActivationCannotResurrectConsumedGeneration() {
        assertTrue(SwitchifyRemoteBridgeCoordinator.setRepeatActive(20, true))
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
        assertFalse(SwitchifyRemoteBridgeCoordinator.setRepeatActive(20, true))
        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
    }

    @Test fun delayedForwardingActivationCannotResurrectStoppedGeneration() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(20, true))
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(20, false))
        assertFalse(SwitchifyRemoteBridgeCoordinator.setForwardingActive(20, true))
        assertFalse(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, true, 1, 1, false))
    }

    @Test fun unconfiguredExternalSwitchDoesNotStopRemoteRepeat() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setRepeatActive(20, true))
        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(31))
        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(30))
    }

    @Test fun configuredSwitchChangeStopsActiveForwarding() {
        var configured = listOf(30 to "USB switch")
        SwitchifyRemoteBridgeCoordinator.attach { configured }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(20, true))
        configured = listOf(30 to "Renamed USB switch")
        SwitchifyRemoteBridgeCoordinator.configuredSwitchesChanged()
        assertFalse(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, true, 1, 1, false))
        assertFalse(SwitchifyRemoteBridgeCoordinator.setForwardingActive(20, true))
    }

    @Test fun identicalConfiguredSwitchReloadPreservesActiveForwarding() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(20, true))
        SwitchifyRemoteBridgeCoordinator.configuredSwitchesChanged()
        assertTrue(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, true, 1, 1, false))
    }
}
