package com.enaboapps.switchify.service.remotebridge

import com.enaboapps.switchify.pc.remote.PcSwitchRepeatStop
import com.enaboapps.switchify.pc.remote.PcSwitchStopHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeScanningHoldTarget(var pausedByUser: Boolean = false) : ScanningHoldTarget {
    val calls = mutableListOf<String>()

    override fun pauseScanning() { calls += "pause" }
    override fun resumeScanning() { calls += "resume" }
    override fun isPausedByUser() = pausedByUser
}

class ScanningHoldCoordinatorTest {
    private val posted = ArrayDeque<() -> Unit>()
    private val target = FakeScanningHoldTarget()
    private val handles = mutableListOf<PcSwitchStopHandle>()

    @Before fun setUp() {
        SwitchifyRemoteBridgeCoordinator.resetForTests()
        SwitchifyRemoteBridgeCoordinator.postToMain = { posted += it }
        SwitchifyRemoteBridgeCoordinator.scanningHoldTarget = { target }
    }

    @After fun cleanup() {
        handles.forEach { it.release() }
        PcSwitchRepeatStop.availability = { false }
        PcSwitchRepeatStop.armedListener = {}
        SwitchifyRemoteBridgeCoordinator.detach()
        SwitchifyRemoteBridgeCoordinator.resetForTests()
    }

    private fun runMainLooper() {
        while (posted.isNotEmpty()) posted.removeFirst()()
    }

    @Test fun holdChangesApplyOnTheMainLooperRatherThanTheCallingThread() {
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, true)
        assertEquals(emptyList<String>(), target.calls)
        runMainLooper()
        assertEquals(listOf("pause"), target.calls)
    }

    @Test fun forwardingAndPcRepeatHoldsCombineUntilBothRelease() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1, true))
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, true)
        runMainLooper()
        assertEquals(listOf("pause"), target.calls)

        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(1, false))
        runMainLooper()
        assertEquals(listOf("pause"), target.calls)

        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, false)
        runMainLooper()
        assertEquals(listOf("pause", "resume"), target.calls)
    }

    @Test fun releasingHoldsDoesNotResumeOverTheUsersOwnPause() {
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, true)
        runMainLooper()
        target.pausedByUser = true
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, false)
        runMainLooper()
        assertEquals(listOf("pause"), target.calls)

        target.pausedByUser = false
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.Forwarding, true)
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.Forwarding, false)
        runMainLooper()
        assertEquals(listOf("pause", "pause", "resume"), target.calls)
    }

    @Test fun holdsTrackedWithoutAScanningManagerStillGateResume() {
        var available: ScanningHoldTarget? = null
        SwitchifyRemoteBridgeCoordinator.scanningHoldTarget = { available }
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, true)
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.Forwarding, true)
        runMainLooper()
        available = target
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, false)
        runMainLooper()
        assertEquals(emptyList<String>(), target.calls)
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.Forwarding, false)
        runMainLooper()
        assertEquals(listOf("resume"), target.calls)
    }

    @Test fun detachResetsHoldsSoTheyCannotSurviveAServiceRestart() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, true)
        runMainLooper()
        SwitchifyRemoteBridgeCoordinator.detach()
        runMainLooper()

        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(2, true))
        runMainLooper()
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(2, false))
        runMainLooper()
        assertEquals(listOf("pause", "pause", "resume"), target.calls)
    }

    @Test fun holdPostedBeforeDetachIsDroppedAfterIt() {
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.PcRepeat, true)
        SwitchifyRemoteBridgeCoordinator.detach()
        runMainLooper()
        assertEquals(emptyList<String>(), target.calls)

        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.Forwarding, true)
        SwitchifyRemoteBridgeCoordinator.setScanningHeld(ScanningHold.Forwarding, false)
        runMainLooper()
        assertEquals(listOf("pause", "resume"), target.calls)
    }

    @Test fun pcRepeatHookHoldsScanningWhileArmedAndReportsSwitchAvailability() {
        PcSwitchRepeatStop.bindToSwitchBridge()
        assertFalse(PcSwitchRepeatStop.hook.isAvailable())
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(PcSwitchRepeatStop.hook.isAvailable())

        var stops = 0
        val handle = PcSwitchRepeatStop.hook.arm { stops += 1 }
        handles += handle
        runMainLooper()
        assertEquals(listOf("pause"), target.calls)

        assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForExternalSwitch(30))
        assertEquals(1, stops)
        handle.release()
        runMainLooper()
        assertEquals(listOf("pause", "resume"), target.calls)

        SwitchifyRemoteBridgeCoordinator.detach()
        assertFalse(PcSwitchRepeatStop.hook.isAvailable())
    }

    @Test fun pcRepeatAndForwardingThroughTheRealHooksShareOneHold() {
        PcSwitchRepeatStop.bindToSwitchBridge()
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        val handle = PcSwitchRepeatStop.hook.arm {}
        handles += handle
        assertTrue(SwitchifyRemoteBridgeCoordinator.setInAppForwardingActive(5, true))
        handle.release()
        runMainLooper()
        assertEquals(listOf("pause"), target.calls)
        assertTrue(SwitchifyRemoteBridgeCoordinator.setInAppForwardingActive(5, false))
        runMainLooper()
        assertEquals(listOf("pause", "resume"), target.calls)
    }

    @Test fun bridgeServiceUnbindClearsOnlySwitchifyRemoteState() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setRepeatActive(9, true))
        assertTrue(SwitchifyRemoteBridgeCoordinator.setInAppForwardingActive(3, true))
        runMainLooper()

        assertFalse(SwitchifyRemoteBridgeService().onUnbind(null))
        runMainLooper()
        assertEquals(SwitchForwardingOwner.InApp, SwitchifyRemoteBridgeCoordinator.activeForwardingOwner())
        assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
        assertEquals(listOf("pause"), target.calls)
    }

    @Test fun bridgeServiceUnbindReleasesSwitchifyRemoteForwardingHold() {
        SwitchifyRemoteBridgeCoordinator.attach { listOf(30 to "USB switch") }
        assertTrue(SwitchifyRemoteBridgeCoordinator.setForwardingActive(4, true))
        runMainLooper()

        assertFalse(SwitchifyRemoteBridgeService().onUnbind(null))
        runMainLooper()
        assertNull(SwitchifyRemoteBridgeCoordinator.activeForwardingOwner())
        assertEquals(listOf("pause", "resume"), target.calls)
    }
}
