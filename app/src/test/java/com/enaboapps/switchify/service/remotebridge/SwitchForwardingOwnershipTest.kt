package com.enaboapps.switchify.service.remotebridge

import com.enaboapps.switchify.pc.forwarding.InAppSwitchBridge
import com.enaboapps.switchify.pc.forwarding.PcExternalSwitch
import com.enaboapps.switchify.pc.forwarding.PcSwitchBridgeEvent
import com.enaboapps.switchify.pc.forwarding.PcSwitchBridgeSnapshot
import com.enaboapps.switchify.service.switches.external.ExternalSwitchRemoteDiversion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SwitchForwardingOwnershipTest {
    private val scanningPauses = mutableListOf<Boolean>()
    private val events = mutableListOf<PcSwitchBridgeEvent>()
    private var unsubscribe: () -> Unit = {}

    @Before fun setUp() {
        SwitchifyRemoteBridgeCoordinator.setScanningPaused = { scanningPauses += it }
        unsubscribe = InAppSwitchBridge.subscribe { events += it }
    }

    @After fun cleanup() {
        unsubscribe()
        SwitchifyRemoteBridgeCoordinator.detach()
        SwitchifyRemoteBridgeCoordinator.resetForTests()
        InAppSwitchBridge.resetForTests()
    }

    @Test fun inAppForwardingReceivesSequencedEdgesAndPausesScanning() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        val generation = InAppSwitchBridge.nextGeneration()
        assertTrue(InAppSwitchBridge.setForwardingActive(generation, true))
        assertEquals(SwitchForwardingOwner.InApp, SwitchifyRemoteBridgeCoordinator.activeForwardingOwner())
        assertFalse(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(31, true, 1, 1, false))
        assertTrue(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, true, 2, 2, false))
        assertTrue(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, true, 2, 3, false))
        assertTrue(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, false, 2, 10, true))
        assertEquals(
            listOf(
                PcSwitchBridgeEvent.SwitchEdge(generation, 1, 30, true, 2, 2, false),
                PcSwitchBridgeEvent.SwitchEdge(generation, 2, 30, false, 2, 10, true)
            ),
            events.filterIsInstance<PcSwitchBridgeEvent.SwitchEdge>()
        )
        assertTrue(InAppSwitchBridge.setForwardingActive(generation, false))
        assertNull(SwitchifyRemoteBridgeCoordinator.activeForwardingOwner())
        assertEquals(listOf(true, false), scanningPauses)
    }

    @Test fun onlyOneForwarderOwnsTheSwitchesAtATime() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1_700_000_000_000, true))
        assertTrue(InAppSwitchBridge.forwardingOwnedBySwitchifyRemote())
        val generation = InAppSwitchBridge.nextGeneration()
        assertFalse(InAppSwitchBridge.setForwardingActive(generation, true))
        assertFalse(InAppSwitchBridge.setForwardingActive(1_700_000_000_000, false))
        assertTrue(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, true, 1, 1, false))
        assertTrue(events.none { it is PcSwitchBridgeEvent.SwitchEdge })
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1_700_000_000_000, false))

        val next = InAppSwitchBridge.nextGeneration()
        assertTrue(InAppSwitchBridge.setForwardingActive(next, true))
        assertFalse(InAppSwitchBridge.forwardingOwnedBySwitchifyRemote())
        assertFalse(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1_700_000_000_001, true))
        assertFalse(SwitchifyRemoteBridgeCoordinator.setForwardingActive(next, false))
        assertEquals(SwitchForwardingOwner.InApp, SwitchifyRemoteBridgeCoordinator.activeForwardingOwner())
    }

    @Test fun inAppGenerationsAreIndependentOfSwitchifyRemoteGenerations() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1_700_000_000_000, true))
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1_700_000_000_000, false))
        val generation = InAppSwitchBridge.nextGeneration()
        assertTrue(InAppSwitchBridge.setForwardingActive(generation, true))
        assertTrue(InAppSwitchBridge.setForwardingActive(generation, false))
        assertFalse(InAppSwitchBridge.setForwardingActive(generation, true))
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1_700_000_000_001, true))
    }

    @Test fun switchifyRemoteUnbindLeavesInAppForwardingRunning() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        val generation = InAppSwitchBridge.nextGeneration()
        assertTrue(InAppSwitchBridge.setForwardingActive(generation, true))
        SwitchifyRemoteBridgeCoordinator.clearRemoteActive()
        assertEquals(SwitchForwardingOwner.InApp, SwitchifyRemoteBridgeCoordinator.activeForwardingOwner())
        assertTrue(events.none { it is PcSwitchBridgeEvent.Revoked })
        assertEquals(listOf(true), scanningPauses)
    }

    @Test fun switchifyRemoteUnbindStillClearsItsOwnForwarding() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(20, true))
        SwitchifyRemoteBridgeCoordinator.clearRemoteActive()
        assertNull(SwitchifyRemoteBridgeCoordinator.activeForwardingOwner())
        assertEquals(listOf(true, false), scanningPauses)
    }

    @Test fun serviceCleanupRevokesInAppForwardingAndResumesScanning() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        val generation = InAppSwitchBridge.nextGeneration()
        assertTrue(InAppSwitchBridge.setForwardingActive(generation, true))
        SwitchifyRemoteBridgeCoordinator.clearActive()
        SwitchifyRemoteBridgeCoordinator.clearActive()
        assertEquals(listOf<PcSwitchBridgeEvent>(PcSwitchBridgeEvent.Revoked(generation)), events.filterIsInstance<PcSwitchBridgeEvent.Revoked>())
        assertEquals(listOf(true, false), scanningPauses)
        assertFalse(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(30, true, 1, 1, false))
    }

    @Test fun snapshotsReachTheInAppForwarder() {
        var configured = listOf(31 to "Second", 30 to "First")
        SwitchifyRemoteBridgeCoordinator.attach { configured }
        assertEquals(
            PcSwitchBridgeSnapshot(true, listOf(PcExternalSwitch(31, "Second"), PcExternalSwitch(30, "First"))),
            InAppSwitchBridge.snapshot()
        )
        val generation = InAppSwitchBridge.nextGeneration()
        assertTrue(InAppSwitchBridge.setForwardingActive(generation, true))
        configured = listOf(30 to "Renamed")
        SwitchifyRemoteBridgeCoordinator.configuredSwitchesChanged()
        assertNull(SwitchifyRemoteBridgeCoordinator.activeForwardingOwner())
        assertEquals(listOf<PcSwitchBridgeEvent>(PcSwitchBridgeEvent.Revoked(generation)), events.filterIsInstance<PcSwitchBridgeEvent.Revoked>())
        SwitchifyRemoteBridgeCoordinator.detach()
        val snapshots = events.filterIsInstance<PcSwitchBridgeEvent.Snapshot>().map { it.snapshot }
        assertEquals(
            listOf(
                PcSwitchBridgeSnapshot(true, listOf(PcExternalSwitch(31, "Second"), PcExternalSwitch(30, "First"))),
                PcSwitchBridgeSnapshot(true, listOf(PcExternalSwitch(30, "Renamed"))),
                PcSwitchBridgeSnapshot.Unavailable
            ),
            snapshots
        )
        assertEquals(PcSwitchBridgeSnapshot.Unavailable, InAppSwitchBridge.snapshot())
        assertFalse(InAppSwitchBridge.setForwardingActive(InAppSwitchBridge.nextGeneration(), true))
    }

    @Test fun releaseOfANormallyHandledPressIsSuppressedWhenInAppForwardingActivates() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        val diversion = ExternalSwitchRemoteDiversion()
        assertTrue(diversion.onPressed(30, 1, 1) { true })
        assertTrue(InAppSwitchBridge.setForwardingActive(InAppSwitchBridge.nextGeneration(), true))
        var handledNormally = false
        assertTrue(diversion.onReleased(30, 1, 2, false, normalHandling = { handledNormally = true; true }))
        assertFalse(handledNormally)
        assertTrue(events.none { it is PcSwitchBridgeEvent.SwitchEdge })
        assertTrue(diversion.onPressed(30, 5, 5) { false })
        assertEquals(1, events.count { it is PcSwitchBridgeEvent.SwitchEdge })
    }

    @Test fun activationTokenChangesWhenForwardingMovesBetweenOwnersWithTheSameGeneration() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1, true))
        val remoteActivation = SwitchifyRemoteBridgeCoordinator.activeForwardingGeneration()
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1, false))
        assertTrue(InAppSwitchBridge.setForwardingActive(1, true))
        val inAppActivation = SwitchifyRemoteBridgeCoordinator.activeForwardingGeneration()
        assertTrue(remoteActivation != 0L && inAppActivation != 0L && remoteActivation != inAppActivation)
    }
}
