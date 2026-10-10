package com.enaboapps.switchify.pc.forwarding

import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcProfileStatus
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.transport.PcDiscoveredDesktop
import com.enaboapps.switchify.pc.transport.PcUnsubscribe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcForwardingSessionTest {
    private val desktop = PcDiscoveredDesktop("desktop", "Desk", PcPlatform.Windows, null, "peripheral", null)

    private class Harness(
        val connectionState: MutableStateFlow<PcConnectionState>,
        val connection: FakeConnection,
        val bridge: FakeBridge,
        val preferences: FakePreferences,
        val cleanups: MutableList<suspend () -> Unit>,
        val session: PcForwardingSession
    )

    private fun connected(profileCommands: List<String> = GENERIC_COMMANDS) =
        PcConnectionState.Connected(desktop, pointerProfile(profileCommands), PcProfileStatus.Ready)

    private fun TestScope.harness(connection: FakeConnection = FakeConnection(), attached: Boolean = true): Harness {
        val state = MutableStateFlow<PcConnectionState>(connected())
        val bridge = FakeBridge()
        val preferences = FakePreferences()
        val cleanups = mutableListOf<suspend () -> Unit>()
        val session = PcForwardingSession(
            connectionState = state,
            connection = connection,
            registerCleanup = { cleanup ->
                cleanups += cleanup
                PcUnsubscribe { cleanups -= cleanup }
            },
            bridge = bridge,
            preferences = preferences,
            scope = backgroundScope,
            sessionIds = { "00000000-0000-4000-8000-000000000001" }
        )
        if (attached) session.attach()
        runCurrent()
        return Harness(state, connection, bridge, preferences, cleanups, session)
    }

    @Test
    fun loadsTheRememberedProfileForTheConnectedPc() = runTest {
        val connection = FakeConnection(catalog(keyboardProfile, scanningProfile))
        val state = MutableStateFlow<PcConnectionState>(connected())
        val preferences = FakePreferences().apply { profiles["desktop"] = scanningProfile.id }
        val session = PcForwardingSession(state, connection, { PcUnsubscribe {} }, FakeBridge(), preferences, backgroundScope)
        runCurrent()
        assertEquals(scanningProfile.id, session.state.value.selectedProfileId)
    }

    @Test
    fun savesTheProfileSelectionPerPc() = runTest {
        val h = harness(FakeConnection(catalog(keyboardProfile, scanningProfile)))
        h.session.selectProfile(scanningProfile.id)
        runCurrent()
        assertEquals(scanningProfile.id, h.preferences.profiles["desktop"])
        assertEquals(scanningProfile.id, h.session.state.value.selectedProfileId)
        h.preferences.failSaves = true
        h.session.selectProfile(keyboardProfile.id)
        runCurrent()
        assertEquals(scanningProfile.id, h.session.state.value.selectedProfileId)
        assertEquals(PcForwardingMessage.SelectionSaveFailed, h.session.state.value.message)
    }

    @Test
    fun restartsKeyboardForwardingAfterAnUnexpectedReconnect() = runTest {
        val h = harness()
        h.session.toggle()
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.session.state.value.phase)
        h.connectionState.value = PcConnectionState.Reconnecting(desktop, 1)
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertTrue(h.cleanups.isEmpty())
        h.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.session.state.value.phase)
        assertEquals(2, h.connection.of("switch.session.start").size)
    }

    @Test
    fun doesNotRestartWhenTheProfileVersionChanged() = runTest {
        val connection = FakeConnection()
        val h = harness(connection)
        h.session.toggle()
        runCurrent()
        h.connectionState.value = PcConnectionState.Reconnecting(desktop, 1)
        runCurrent()
        connection.response = catalog(keyboardProfile.copy(version = 3))
        h.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(PcForwardingMessage.ProfileChanged, h.session.state.value.message)
        assertEquals(1, connection.of("switch.session.start").size)
    }

    @Test
    fun doesNotRestartScanningOrAfterAManualStop() = runTest {
        val scanning = harness(FakeConnection(catalog(scanningProfile)))
        scanning.session.toggle()
        runCurrent()
        assertEquals(PcForwardingPhase.Active, scanning.session.state.value.phase)
        scanning.connectionState.value = PcConnectionState.Reconnecting(desktop, 1)
        runCurrent()
        scanning.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, scanning.session.state.value.phase)

        val manual = harness()
        manual.session.toggle()
        runCurrent()
        manual.session.toggle()
        runCurrent()
        manual.connectionState.value = PcConnectionState.Reconnecting(desktop, 1)
        runCurrent()
        manual.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, manual.session.state.value.phase)
        assertEquals(1, manual.connection.of("switch.session.start").size)
    }

    @Test
    fun aDisconnectRunsTheRegisteredCleanupAndForgetsTheRestoreIntent() = runTest {
        val h = harness()
        h.session.toggle()
        runCurrent()
        assertEquals(1, h.cleanups.size)
        h.cleanups.toList().forEach { it() }
        runCurrent()
        assertEquals(1, h.connection.of("switch.session.stop").size)
        assertEquals(listOf(41L to true, 41L to false), h.bridge.active)
        h.connectionState.value = PcConnectionState.Idle(emptyList())
        runCurrent()
        assertTrue(h.cleanups.isEmpty())
        h.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(1, h.connection.of("switch.session.start").size)
    }

    @Test
    fun leavingTheSurfaceThenReconnectingDoesNotRestart() = runTest {
        val h = harness()
        h.session.toggle()
        runCurrent()
        h.session.detach(changingConfigurations = false)
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(PcForwardingMessage.LeftScreen, h.session.state.value.message)
        h.connectionState.value = PcConnectionState.Reconnecting(desktop, 1)
        runCurrent()
        h.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(1, h.connection.of("switch.session.start").size)
        h.session.attach()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(1, h.connection.of("switch.session.start").size)
    }

    @Test
    fun reconnectingThenLeavingTheSurfaceDoesNotRestart() = runTest {
        val h = harness()
        h.session.toggle()
        runCurrent()
        h.connectionState.value = PcConnectionState.Reconnecting(desktop, 1)
        runCurrent()
        h.session.detach(changingConfigurations = false)
        runCurrent()
        h.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(1, h.connection.of("switch.session.start").size)
        h.session.attach()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(1, h.connection.of("switch.session.start").size)
    }

    @Test
    fun aReconnectWhileTheSurfaceIsHiddenNeverStartsForwarding() = runTest {
        val h = harness()
        h.session.toggle()
        runCurrent()
        h.connectionState.value = PcConnectionState.Reconnecting(desktop, 1)
        runCurrent()
        h.session.detach(changingConfigurations = true)
        h.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(1, h.connection.of("switch.session.start").size)
        assertTrue(h.bridge.active.none { it.second && it.first > 41L })
    }

    @Test
    fun aConfigurationChangeKeepsForwardingAndRestoresOnlyOnceTheSurfaceReturns() = runTest {
        val h = harness()
        h.session.toggle()
        runCurrent()
        h.session.detach(changingConfigurations = true)
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.session.state.value.phase)
        assertTrue(h.connection.of("switch.session.stop").isEmpty())
        h.connectionState.value = PcConnectionState.Reconnecting(desktop, 1)
        runCurrent()
        h.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(1, h.connection.of("switch.session.start").size)
        h.session.attach()
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.session.state.value.phase)
        assertEquals(2, h.connection.of("switch.session.start").size)
        h.session.detach(changingConfigurations = true)
        h.session.attach()
        runCurrent()
        assertEquals(2, h.connection.of("switch.session.start").size)
    }

    @Test
    fun startingWhileHiddenKeepsNoRestoreIntent() = runTest {
        val h = harness(attached = false)
        h.session.toggle()
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.session.state.value.phase)
        h.connectionState.value = PcConnectionState.Reconnecting(desktop, 1)
        runCurrent()
        h.connectionState.value = connected()
        h.session.attach()
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.session.state.value.phase)
        assertEquals(1, h.connection.of("switch.session.start").size)
    }

    @Test
    fun anEqualProfileFromTheSamePcKeepsTheRunningController() = runTest {
        val h = harness()
        h.session.toggle()
        runCurrent()
        h.connectionState.value = connected()
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.session.state.value.phase)
        assertEquals(1, h.connection.of("switch.session.start").size)
        assertTrue(h.connection.of("switch.session.stop").isEmpty())
    }

    @Test
    fun closingStopsTheActiveSession() = runTest {
        val h = harness()
        h.session.toggle()
        runCurrent()
        h.session.close().join()
        assertEquals(1, h.connection.of("switch.session.stop").size)
        assertFalse(h.bridge.active.last().second)
        assertTrue(h.cleanups.isEmpty())
    }

    @Test
    fun holdToStopIsLimitedToTheOfferedDurations() = runTest {
        val h = harness()
        h.session.setHoldToStopMs(8_000)
        assertEquals(8_000L, h.session.holdToStopMs.value)
        h.session.setHoldToStopMs(4_000)
        assertEquals(PcForwardingController.DEFAULT_HOLD_TO_STOP_MS, h.session.holdToStopMs.value)
    }

    @Test
    fun restoreIsKeptOnlyWhileConnectedOrReconnecting() {
        assertFalse(PcForwardingRestoreState.shouldClear(connected()))
        assertFalse(PcForwardingRestoreState.shouldClear(PcConnectionState.Reconnecting(desktop, 1)))
        assertTrue(PcForwardingRestoreState.shouldClear(PcConnectionState.Idle(emptyList())))
        assertTrue(PcForwardingRestoreState.shouldClear(PcConnectionState.Connecting(desktop)))
        val restore = PcForwardingRestoreState()
        val intent = PcForwardingRestoreIntent("desktop", "profile", 2)
        restore.set(intent)
        assertEquals(intent, restore.get())
        restore.clear()
        assertNull(restore.get())
    }
}
