package com.enaboapps.switchify.pc.forwarding

import com.enaboapps.switchify.pc.protocol.PcCommandTypes
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcForwardingControllerTest {
    private class Harness(
        val bridge: FakeBridge,
        val connection: FakeConnection,
        val controller: PcForwardingController,
        val safetyStops: () -> Int
    )

    private fun TestScope.harness(
        commands: List<String> = GENERIC_COMMANDS,
        noAckCommands: List<String> = emptyList(),
        switchScanning: Boolean = false,
        connection: FakeConnection = FakeConnection(),
        bridge: FakeBridge = FakeBridge(),
        holdToStopMs: Long = 5_000,
        sessionId: String = "00000000-0000-4000-8000-000000000001"
    ): Harness {
        var safetyStops = 0
        val controller = PcForwardingController(
            connection = connection,
            bridge = bridge,
            pointerProfile = pointerProfile(commands, noAckCommands, switchScanning),
            scope = backgroundScope,
            holdToStopMs = { holdToStopMs },
            sessionIds = { sessionId },
            onSafetyStop = { safetyStops += 1 }
        )
        return Harness(bridge, connection, controller) { safetyStops }
    }

    private fun scanningHarness(scope: TestScope, connection: FakeConnection = FakeConnection(catalog(scanningProfile))) =
        scope.harness(noAckCommands = listOf(PcCommandTypes.SWITCH_EDGE), switchScanning = true, connection = connection)

    @Test
    fun stopsAScanningSessionOnHoldToStopWithoutSendingASelectingRelease() = runTest {
        val h = scanningHarness(this)
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        h.bridge.edge(2, 20, down = false, downTimeMs = 0, eventTimeMs = 4_999)
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.controller.state.value.phase)
        h.bridge.edge(3, 20, down = true, downTimeMs = 6_000, eventTimeMs = 6_000)
        runCurrent()
        h.bridge.edge(4, 20, down = false, downTimeMs = 6_000, eventTimeMs = 11_000)
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.controller.state.value.phase)
        assertEquals(PcForwardingMessage.HeldToStop, h.controller.state.value.message)
        assertEquals(listOf("down", "up", "down"), h.connection.edgeStates())
        assertEquals(1, h.safetyStops())
        val ordered = h.connection.sent.map { it.type }.filter { it == "switch.edge" || it == "switch.session.stop" }
        assertEquals("switch.session.stop", ordered.last())
        assertEquals(1, ordered.count { it == "switch.session.stop" })
        h.controller.cleanup()
    }

    @Test
    fun keepsAScanningSessionAliveAndWithdrawsACancelledPressWithASync() = runTest {
        val h = scanningHarness(this)
        h.controller.loadProfiles()
        assertEquals(mapOf("includeScanning" to true), h.connection.requests.single().payload)
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        h.bridge.edge(2, 20, down = false, downTimeMs = 0, eventTimeMs = 20, cancelled = true)
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.controller.state.value.phase)
        assertTrue(h.connection.of("switch.session.stop").isEmpty())
        assertEquals(listOf("down"), h.connection.edgeStates())
        assertEquals(emptyList<Int>(), h.connection.of("switch.sync").last().payload["pressedSwitchIds"])
        assertFalse(h.controller.state.value.mappings.first { it.keyCode == 20 }.pressed)
        h.controller.cleanup()
    }

    @Test
    fun keepsAScanningSessionAliveAndWithdrawsAReplacedPressBeforePressingAgain() = runTest {
        val h = scanningHarness(this)
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        h.bridge.edge(2, 20, down = true, downTimeMs = 1, eventTimeMs = 20)
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.controller.state.value.phase)
        assertTrue(h.connection.of("switch.session.stop").isEmpty())
        assertEquals(listOf("down", "down"), h.connection.edgeStates())
        val calls = h.connection.sent.filter { it.type == "switch.sync" || it.type == "switch.edge" }
        val withdraw = calls.indexOfFirst {
            it.type == "switch.sync" && it.payload["pressedSwitchIds"] == emptyList<Int>() && (it.payload["sequence"] as Long) > 2
        }
        assertTrue(withdraw > 0)
        assertEquals("switch.edge", calls[withdraw + 1].type)
        assertEquals("down", calls[withdraw + 1].payload["state"])
        h.controller.cleanup()
    }

    @Test
    fun scanningEdgesAlwaysRequestAcknowledgement() = runTest {
        val h = scanningHarness(this)
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        assertEquals(PcResponseMode.Ack, h.connection.of("switch.edge").single().responseMode)
        h.controller.cleanup()
    }

    @Test
    fun doesNotArmTheIdleStopForScanningProfiles() = runTest {
        val h = scanningHarness(this)
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        advanceTimeBy(PcForwardingController.IDLE_TIMEOUT_MS + 1_000)
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.controller.state.value.phase)
        h.controller.cleanup()
    }

    @Test
    fun stopsKeyboardForwardingAfterSixtySecondsWithoutSwitchActivity() = runTest {
        val h = harness()
        h.controller.loadProfiles()
        h.controller.start()
        advanceTimeBy(PcForwardingController.IDLE_TIMEOUT_MS - 1_000)
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.controller.state.value.phase)
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        advanceTimeBy(PcForwardingController.IDLE_TIMEOUT_MS - 1_000)
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.controller.state.value.phase)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.controller.state.value.phase)
        assertEquals(PcForwardingMessage.IdleTimeout, h.controller.state.value.message)
        assertEquals(1, h.safetyStops())
        assertEquals(1, h.connection.of("switch.session.stop").size)
    }

    @Test
    fun syncsHeldSwitchesEverySecondSoThePcSessionDoesNotExpire() = runTest {
        val h = scanningHarness(this)
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        advanceTimeBy(PC_SESSION_EXPIRY_MS)
        runCurrent()
        val syncs = h.connection.of("switch.sync")
        assertTrue(syncs.size >= (PC_SESSION_EXPIRY_MS / PcForwardingController.SYNC_INTERVAL_MS).toInt())
        assertEquals(listOf(1), syncs.last().payload["pressedSwitchIds"])
        val sequences = h.connection.sent.mapNotNull { it.payload["sequence"] as? Long }
        assertEquals(sequences.sorted(), sequences)
        assertEquals(sequences.distinct(), sequences)
        h.controller.cleanup()
    }

    @Test
    fun stopsScanningWhenThePcRejectsASyncAfterItsSessionExpired() = runTest {
        val connection = FakeConnection(catalog(scanningProfile))
        val h = scanningHarness(this, connection)
        h.controller.loadProfiles()
        h.controller.start()
        connection.onSend = { command, _ -> command.type != "switch.sync" }
        advanceTimeBy(PcForwardingController.SYNC_INTERVAL_MS + 1)
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.controller.state.value.phase)
        assertEquals(PcForwardingMessage.PcScanningStopped, h.controller.state.value.message)
        assertEquals(1, h.safetyStops())
    }

    @Test
    fun preservesQueuedEdgeSemanticsAcrossDelayedAcknowledgementAndStop() = runTest {
        val down = CompletableDeferred<Boolean>()
        val connection = FakeConnection(catalog(scanningProfile))
        connection.onSend = { command, _ -> if (command.type == "switch.edge" && command.payload["state"] == "down") down.await() else true }
        val h = harness(connection = connection)
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        h.bridge.edge(2, 20, down = false, downTimeMs = 0, eventTimeMs = 50)
        runCurrent()
        val stopped = async { h.controller.stop() }
        runCurrent()
        down.complete(true)
        stopped.await()
        assertTrue(connection.sent.none { it.type == "switch.edge" && it.payload["state"] == "up" })
        h.controller.cleanup()
    }

    @Test
    fun preservesQueuedEdgeSemanticsAcrossDelayedAcknowledgementAndSync() = runTest {
        val down = CompletableDeferred<Boolean>()
        val connection = FakeConnection(catalog(scanningProfile))
        connection.onSend = { command, _ -> if (command.type == "switch.edge" && command.payload["state"] == "down") down.await() else true }
        val h = harness(connection = connection)
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        advanceTimeBy(PcForwardingController.SYNC_INTERVAL_MS + 1)
        h.bridge.edge(2, 20, down = false, downTimeMs = 0, eventTimeMs = 50)
        runCurrent()
        down.complete(true)
        runCurrent()
        val syncs = connection.of("switch.sync")
        assertEquals(listOf(1), syncs[1].payload["pressedSwitchIds"])
        assertEquals(1, connection.sent.count { it.type == "switch.edge" && it.payload["state"] == "up" })
        h.controller.cleanup()
    }

    @Test
    fun mapsEightSwitchesSendsOrderedEdgesAndCleansUp() = runTest {
        val h = harness(noAckCommands = listOf(PcCommandTypes.SWITCH_EDGE))
        h.controller.loadProfiles()
        assertTrue(h.controller.start())
        assertEquals(8, h.controller.state.value.mappings.size)
        assertEquals(2, h.controller.state.value.overflow.size)
        assertEquals("Space", h.controller.state.value.mappings.first().outputLabel)
        assertNull(h.controller.state.value.mappings[1].outputLabel)
        h.bridge.edge(1, 20, down = true, downTimeMs = 10, eventTimeMs = 10)
        h.bridge.edge(2, 20, down = false, downTimeMs = 10, eventTimeMs = 20)
        runCurrent()
        val start = h.connection.of("switch.session.start").single().payload
        assertEquals("keyboard", start["profileId"])
        assertEquals(2, start["profileVersion"])
        assertEquals(8, start["switchCount"])
        val firstEdge = h.connection.of("switch.edge").first()
        assertEquals(1, firstEdge.payload["switchId"])
        assertEquals("down", firstEdge.payload["state"])
        assertEquals(2L, firstEdge.payload["sequence"])
        assertEquals(PcResponseMode.None, firstEdge.responseMode)
        h.controller.stop()
        assertEquals(listOf(41L to true, 41L to false), h.bridge.active)
    }

    @Test
    fun usesADesktopCompatibleUuidSessionId() = runTest {
        val bridge = FakeBridge()
        val connection = FakeConnection()
        val controller = PcForwardingController(connection, bridge, pointerProfile(GENERIC_COMMANDS), backgroundScope)
        controller.loadProfiles()
        assertTrue(controller.start())
        val sessionId = connection.of("switch.session.start").single().payload["sessionId"] as String
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE).matches(sessionId))
        controller.cleanup()
    }

    @Test
    fun synthesizesReleaseAndRepressForAReplacementPress() = runTest {
        val h = harness()
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 10, eventTimeMs = 10)
        h.bridge.edge(2, 20, down = true, downTimeMs = 20, eventTimeMs = 20)
        runCurrent()
        assertEquals(listOf("down", "up", "down"), h.connection.edgeStates())
        h.controller.cleanup()
    }

    @Test
    fun failsClosedOnAMissingEdgeAndSupportsLegacyGrid3() = runTest {
        val h = harness(
            commands = listOf(PcCommandTypes.GRID_SWITCH_SET, PcCommandTypes.GRID_SWITCH_SYNC),
            noAckCommands = listOf(PcCommandTypes.GRID_SWITCH_SET),
            connection = FakeConnection(response = null)
        )
        h.controller.loadProfiles()
        assertEquals(listOf(PcForwardingController.LEGACY_GRID3_PROFILE), h.controller.state.value.profiles)
        h.controller.start()
        assertTrue(h.connection.of("switch.session.start").isEmpty())
        h.bridge.edge(2, 20, down = true, downTimeMs = 1, eventTimeMs = 1)
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.controller.state.value.phase)
        assertEquals(PcForwardingMessage.MissedEdge, h.controller.state.value.message)
        assertEquals(emptyList<Int>(), h.connection.of("grid.switch.sync").last().payload["pressedSwitchIds"])
    }

    @Test
    fun legacyGrid3EdgesCarryTheSessionWhenTheDesktopSupportsGridSync() = runTest {
        val h = harness(
            commands = listOf(PcCommandTypes.GRID_SWITCH_SET, PcCommandTypes.GRID_SWITCH_SYNC),
            noAckCommands = listOf(PcCommandTypes.GRID_SWITCH_SET),
            connection = FakeConnection(response = null)
        )
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 21, down = true, downTimeMs = 1, eventTimeMs = 1)
        runCurrent()
        val edge = h.connection.of("grid.switch.set").single()
        assertEquals(
            mapOf("switchId" to 2, "state" to "down", "sessionId" to "00000000-0000-4000-8000-000000000001", "sequence" to 2L),
            edge.payload
        )
        assertEquals(PcResponseMode.None, edge.responseMode)
        h.controller.cleanup()
    }

    @Test
    fun legacyGrid3WithoutSyncReleasesEverySwitchOnStop() = runTest {
        val h = harness(commands = listOf(PcCommandTypes.GRID_SWITCH_SET), connection = FakeConnection(response = null))
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 1, eventTimeMs = 1)
        runCurrent()
        assertEquals(mapOf("switchId" to 1, "state" to "down"), h.connection.of("grid.switch.set").single().payload)
        h.controller.stop()
        val releases = h.connection.of("grid.switch.set").drop(1)
        assertEquals((1..8).toList(), releases.map { it.payload["switchId"] })
        assertTrue(releases.all { it.payload == mapOf("switchId" to it.payload["switchId"], "state" to "up") })
        assertTrue(h.connection.of("grid.switch.sync").isEmpty())
    }

    @Test
    fun requiresCaptureAndStopsAfterAFiveSecondHoldRelease() = runTest {
        val h = harness()
        h.controller.loadProfiles()
        h.bridge.value = PcSwitchBridgeSnapshot(false, emptyList())
        assertFalse(h.controller.start())
        assertEquals(PcForwardingMessage.CaptureUnavailable, h.controller.state.value.message)
        h.bridge.value = PcSwitchBridgeSnapshot(true, listOf(PcExternalSwitch(20, "Switch")))
        assertTrue(h.controller.start())
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        h.bridge.edge(2, 20, down = false, downTimeMs = 0, eventTimeMs = 5_000)
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.controller.state.value.phase)
        assertEquals(1, h.safetyStops())
        assertEquals(listOf("down", "up"), h.connection.edgeStates())
    }

    @Test
    fun honoursTheConfiguredHoldToStopDuration() = runTest {
        val h = harness(holdToStopMs = 3_000)
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0)
        h.bridge.edge(2, 20, down = false, downTimeMs = 0, eventTimeMs = 3_000)
        runCurrent()
        assertEquals(PcForwardingMessage.HeldToStop, h.controller.state.value.message)
    }

    @Test
    fun stopsSafelyWhenTheConfiguredExternalSwitchSetChanges() = runTest {
        val h = harness()
        h.controller.loadProfiles()
        h.controller.start()
        val renamed = listOf(PcExternalSwitch(20, "Renamed switch")) + h.bridge.value.externalSwitches.drop(1)
        h.bridge.emit(PcSwitchBridgeEvent.Snapshot(PcSwitchBridgeSnapshot(true, renamed)))
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.controller.state.value.phase)
        assertEquals(PcForwardingMessage.ConfigurationChanged, h.controller.state.value.message)
        assertEquals(1, h.safetyStops())
    }

    @Test
    fun ignoresAnIdenticalSnapshotWhileForwarding() = runTest {
        val h = harness()
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.emit(PcSwitchBridgeEvent.Snapshot(h.bridge.value))
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.controller.state.value.phase)
        h.controller.cleanup()
    }

    @Test
    fun cannotReactivateAfterCleanupWhileSessionStartIsInFlight() = runTest {
        val pendingStart = CompletableDeferred<Boolean>()
        val connection = FakeConnection()
        connection.onSend = { command, _ -> if (command.type == "switch.session.start") pendingStart.await() else true }
        val h = harness(connection = connection)
        h.controller.loadProfiles()
        val starting = async { h.controller.start() }
        runCurrent()
        h.controller.cleanup()
        pendingStart.complete(true)
        assertFalse(starting.await())
        assertTrue(h.bridge.active.none { it.second })
        assertEquals(PcForwardingPhase.Idle, h.controller.state.value.phase)
    }

    @Test
    fun cancelsAnInFlightStartWhenConfiguredSwitchesChange() = runTest {
        val pendingStart = CompletableDeferred<Boolean>()
        val connection = FakeConnection()
        connection.onSend = { command, _ -> if (command.type == "switch.session.start") pendingStart.await() else true }
        val h = harness(connection = connection)
        h.controller.loadProfiles()
        val starting = async { h.controller.start() }
        runCurrent()
        h.bridge.emit(
            PcSwitchBridgeEvent.Snapshot(
                PcSwitchBridgeSnapshot(true, listOf(PcExternalSwitch(5, "New first switch")) + h.bridge.value.externalSwitches)
            )
        )
        runCurrent()
        pendingStart.complete(true)
        assertFalse(starting.await())
        assertEquals(PcForwardingPhase.Idle, h.controller.state.value.phase)
        assertEquals(1, h.safetyStops())
        assertTrue(h.bridge.active.none { it.second })
    }

    @Test
    fun reportsWhenSwitchifyRemoteAlreadyOwnsTheSwitches() = runTest {
        val bridge = FakeBridge().apply {
            acceptActivation = false
            remoteOwnsSwitches = true
        }
        val h = harness(bridge = bridge)
        h.controller.loadProfiles()
        assertFalse(h.controller.start())
        assertEquals(PcForwardingPhase.Failed, h.controller.state.value.phase)
        assertEquals(PcForwardingMessage.SwitchifyRemoteForwarding, h.controller.state.value.message)
        assertEquals(1, h.connection.of("switch.session.stop").size)
    }

    @Test
    fun reportsWhenSwitchifyCannotForward() = runTest {
        val h = harness(bridge = FakeBridge().apply { acceptActivation = false })
        h.controller.loadProfiles()
        assertFalse(h.controller.start())
        assertEquals(PcForwardingMessage.SwitchifyUnavailable, h.controller.state.value.message)
    }

    @Test
    fun stopsSafelyWhenSwitchifyRevokesForwarding() = runTest {
        val h = harness()
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.emit(PcSwitchBridgeEvent.Revoked(generation = 7))
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.controller.state.value.phase)
        h.bridge.emit(PcSwitchBridgeEvent.Revoked(generation = 41))
        runCurrent()
        assertEquals(PcForwardingPhase.Idle, h.controller.state.value.phase)
        assertEquals(PcForwardingMessage.Revoked, h.controller.state.value.message)
        assertEquals(1, h.safetyStops())
        assertEquals(1, h.connection.of("switch.session.stop").size)
    }

    @Test
    fun ignoresEdgesFromAnotherGenerationAndUnmappedSwitches() = runTest {
        val h = harness()
        h.controller.loadProfiles()
        h.controller.start()
        h.bridge.edge(1, 20, down = true, downTimeMs = 0, eventTimeMs = 0, generation = 40)
        h.bridge.edge(1, 29, down = true, downTimeMs = 0, eventTimeMs = 0)
        runCurrent()
        assertEquals(PcForwardingPhase.Active, h.controller.state.value.phase)
        assertTrue(h.connection.of("switch.edge").isEmpty())
        h.controller.cleanup()
    }

    @Test
    fun selectsTheRememberedProfileAndReportsMissingSupport() = runTest {
        val h = harness(connection = FakeConnection(catalog(keyboardProfile, scanningProfile)))
        h.controller.loadProfiles(remembered = scanningProfile.id)
        assertEquals(scanningProfile.id, h.controller.state.value.selectedProfileId)
        assertEquals(emptyMap<String, Any?>(), h.connection.requests.single().payload)

        val empty = harness(connection = FakeConnection(catalog()))
        empty.controller.loadProfiles()
        assertEquals(PcForwardingMessage.NoProfiles, empty.controller.state.value.message)

        val unsupported = harness(commands = emptyList())
        unsupported.controller.loadProfiles()
        assertEquals(PcForwardingPhase.Failed, unsupported.controller.state.value.phase)
        assertEquals(PcForwardingMessage.Unsupported, unsupported.controller.state.value.message)
    }

    @Test
    fun startingTwiceDoesNotOpenASecondSession() = runTest {
        val h = harness()
        h.controller.loadProfiles()
        assertTrue(h.controller.start())
        assertFalse(h.controller.start())
        assertEquals(1, h.connection.of("switch.session.start").size)
        h.controller.cleanup()
    }

    private companion object {
        const val PC_SESSION_EXPIRY_MS = 5_000L
    }
}
