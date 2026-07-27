package com.enaboapps.switchify.service.pcswitchcontrol

import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcPointerBounds
import com.enaboapps.switchify.pc.PcPointerCapabilities
import com.enaboapps.switchify.pc.PcPointerDeltas
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcProtocol
import com.enaboapps.switchify.pc.PcProfileCatalogResult
import com.enaboapps.switchify.pc.PcSwitchBindingSummary
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcSwitchControlForwarderTest {
    @Test
    fun concurrentGenericStartsSendOneStartCommand() = runTest {
        val startStarted = CompletableDeferred<Unit>()
        val allowStart = CompletableDeferred<Unit>()
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            genericSupported = true,
            startStarted = startStarted,
            allowStartToComplete = allowStart
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        val profile = (forwarder.loadProfileCatalog() as PcSwitchCatalogResult.Loaded)
            .catalog.profiles.single()

        val first = async { forwarder.start(profile, usesLegacyGridProtocol = false) }
        startStarted.await()
        val second = async { forwarder.start(profile, usesLegacyGridProtocol = false) }
        runCurrent()

        assertEquals(1, host.commands.filterIsInstance<PcControlCommand.SwitchSessionStart>().size)
        allowStart.complete(Unit)
        assertEquals(PcSwitchControlStartResult.Started, first.await())
        assertEquals(PcSwitchControlStartResult.Started, second.await())
        assertEquals(1, host.commands.filterIsInstance<PcControlCommand.SwitchSessionStart>().size)
        forwarder.stop()
    }

    @Test
    fun stopQueuedDuringGenericStartStopsTheStartedSession() = runTest {
        val startStarted = CompletableDeferred<Unit>()
        val allowStart = CompletableDeferred<Unit>()
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            genericSupported = true,
            startStarted = startStarted,
            allowStartToComplete = allowStart
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        val profile = (forwarder.loadProfileCatalog() as PcSwitchCatalogResult.Loaded)
            .catalog.profiles.single()

        val start = async { forwarder.start(profile, usesLegacyGridProtocol = false) }
        startStarted.await()
        val stop = async { forwarder.stop() }
        runCurrent()
        allowStart.complete(Unit)

        assertEquals(PcSwitchControlStartResult.Started, start.await())
        stop.await()
        assertFalse(forwarder.state.value.active)
        assertTrue(host.commands.last() is PcControlCommand.SwitchSessionStop)
    }

    @Test
    fun screenSleepStopsSessionWithoutRestoringScanning() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            genericSupported = true
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        val profile = (forwarder.loadProfileCatalog() as PcSwitchCatalogResult.Loaded)
            .catalog.profiles.single()
        assertEquals(PcSwitchControlStartResult.Started, forwarder.start(profile, usesLegacyGridProtocol = false))

        forwarder.requestCloseForScreenSleep()
        runCurrent()

        assertFalse(forwarder.state.value.active)
        assertEquals(0, host.restoreCount)
        assertEquals(1, host.releaseCount)
        assertTrue(host.commands.last() is PcControlCommand.SwitchSessionStop)
    }

    @Test
    fun repeatedScreenSleepCloseReleasesChooserConnectionOnce() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            genericSupported = true
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)

        forwarder.requestCloseForScreenSleep()
        forwarder.requestCloseForScreenSleep()
        runCurrent()

        assertEquals(0, host.restoreCount)
        assertEquals(1, host.releaseCount)
    }

    @Test
    fun changeProfileStopsSessionWithoutReleasingConnection() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            genericSupported = true
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        val profile = (forwarder.loadProfileCatalog() as PcSwitchCatalogResult.Loaded)
            .catalog.profiles.single()
        assertEquals(PcSwitchControlStartResult.Started, forwarder.start(profile, usesLegacyGridProtocol = false))

        forwarder.requestChangeProfile()
        runCurrent()

        assertFalse(forwarder.state.value.active)
        assertEquals(1, host.restoreCount)
        assertEquals(0, host.releaseCount)
    }

    @Test
    fun duplicateStopCannotStopALaterSession() = runTest {
        val stopStarted = CompletableDeferred<Unit>()
        val allowStop = CompletableDeferred<Unit>()
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            genericSupported = true,
            stopStarted = stopStarted,
            allowStopToComplete = allowStop
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        val profile = (forwarder.loadProfileCatalog() as PcSwitchCatalogResult.Loaded)
            .catalog.profiles.single()
        assertEquals(PcSwitchControlStartResult.Started, forwarder.start(profile, usesLegacyGridProtocol = false))

        val firstStop = async(start = CoroutineStart.UNDISPATCHED) { forwarder.stop() }
        val duplicateStop = async(start = CoroutineStart.UNDISPATCHED) { forwarder.stop() }
        runCurrent()
        stopStarted.await()
        val nextStart = async { forwarder.start(profile, usesLegacyGridProtocol = false) }
        allowStop.complete(Unit)

        firstStop.await()
        duplicateStop.await()
        assertEquals(PcSwitchControlStartResult.Started, nextStart.await())
        assertTrue(forwarder.state.value.active)
        forwarder.stop()
    }

    @Test
    fun genericProfileStartsBeforeDiversionAndUsesGenericEdges() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            genericSupported = true
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        val loaded = forwarder.loadProfileCatalog() as PcSwitchCatalogResult.Loaded
        val selected = loaded.catalog.profiles.single()

        assertEquals(0, host.suspendCount)
        assertEquals(PcSwitchControlStartResult.Started, forwarder.start(selected, usesLegacyGridProtocol = false))
        assertEquals(1, host.suspendCount)
        assertTrue(host.commands.first() is PcControlCommand.SwitchSessionStart)

        forwarder.onSwitchPressed(20)
        forwarder.onSwitchReleased(20)
        runCurrent()

        assertEquals(
            listOf(true, false),
            host.realtimeCommands.filterIsInstance<PcControlCommand.SwitchEdge>().map { it.down }
        )
        forwarder.stop()
        assertTrue(host.commands.last() is PcControlCommand.SwitchSessionStop)
    }

    @Test
    fun keepsUnassignedOutputOutOfForwardingState() = runTest {
        val host = FakeHost(
            mutableListOf(
                switchEvent("20", "Primary"),
                switchEvent("21", "Secondary")
            ),
            genericSupported = true
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        val profile = (forwarder.loadProfileCatalog() as PcSwitchCatalogResult.Loaded)
            .catalog.profiles.single()

        assertEquals(PcSwitchControlStartResult.Started, forwarder.start(profile, usesLegacyGridProtocol = false))
        assertEquals(
            listOf("Space", null),
            forwarder.state.value.mappings.map { it.outputLabel }
        )

        forwarder.stop()
    }

    @Test
    fun mapsEightExternalSwitchesInNumericThenLexicalOrderAndFreezesSession() = runTest {
        val host = FakeHost(
            mutableListOf(
                switchEvent("10", "Ten"),
                switchEvent("A", "A"),
                switchEvent("2", "Two"),
                switchEvent("1", "One"),
                switchEvent("3", "Three"),
                switchEvent("4", "Four"),
                switchEvent("5", "Five"),
                switchEvent("6", "Six"),
                switchEvent("7", "Seven"),
                switchEvent("8", "Eight"),
                switchEvent("9", "Camera", SWITCH_EVENT_TYPE_CAMERA)
            )
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)

        assertEquals(PcSwitchControlStartResult.Started, forwarder.startLegacyGridProfile())
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8"),
            forwarder.state.value.mappings.map { it.keyCode }
        )
        assertEquals(listOf("Ten", "A"), forwarder.state.value.overflowSwitches)

        host.switches.clear()
        host.switches += switchEvent("99", "New")
        assertTrue(forwarder.onSwitchPressed(99))
        assertTrue(forwarder.onSwitchPressed(1))
        runCurrent()

        assertEquals(listOf(PcControlCommand.LegacyGridSwitchSet(1, true)), host.commands)
        forwarder.stop()
    }

    @Test
    fun forwardsOrderedDeduplicatedDownAndUpWithLiveFeedback() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("20", "Primary")))
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()

        assertTrue(forwarder.onSwitchPressed(20))
        assertTrue(forwarder.state.value.mappings.single().pressed)
        assertTrue(forwarder.onSwitchPressed(20))
        assertTrue(forwarder.onSwitchReleased(20))
        assertFalse(forwarder.state.value.mappings.single().pressed)
        assertTrue(forwarder.onSwitchReleased(20))
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
        forwarder.stop()
    }

    @Test
    fun sequencedDeliveryUsesRealtimeEdgesAndAcknowledgedSnapshots() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            sequencedSupported = true
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        runCurrent()
        host.commands.clear()
        host.realtimeCommands.clear()

        forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 1_000L)
        forwarder.onSwitchReleased(
            20,
            downTimeMs = 1_000L,
            eventTimeMs = 1_100L,
            cancelled = false
        )
        runCurrent()

        val edges = host.realtimeCommands.filterIsInstance<PcControlCommand.LegacyGridSwitchSet>()
        assertEquals(2, edges.size)
        assertTrue(edges.first().down)
        assertFalse(edges.last().down)
        assertEquals(edges.first().sessionId, edges.last().sessionId)
        assertTrue(requireNotNull(edges.first().sequence) < requireNotNull(edges.last().sequence))

        advanceTimeBy(PcSwitchControlForwarder.SNAPSHOT_INTERVAL_MS)
        runCurrent()

        val sync = host.commands.filterIsInstance<PcControlCommand.LegacyGridSwitchSync>().last()
        assertTrue(sync.pressedSwitchIds.isEmpty())
        assertTrue(sync.sequence > requireNotNull(edges.last().sequence))
        forwarder.stop()
    }

    @Test
    fun acknowledgedSnapshotDoesNotBlockRealtimeRelease() = runTest {
        val syncStarted = CompletableDeferred<Unit>()
        val allowSyncToComplete = CompletableDeferred<Unit>()
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            sequencedSupported = true,
            syncStarted = syncStarted,
            allowSyncToComplete = allowSyncToComplete
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        runCurrent()
        syncStarted.await()

        forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 1_000L)
        forwarder.onSwitchReleased(20, downTimeMs = 1_000L, eventTimeMs = 1_100L, cancelled = false)
        runCurrent()

        val edges = host.realtimeCommands.filterIsInstance<PcControlCommand.LegacyGridSwitchSet>()
        assertEquals(listOf(true, false), edges.map { it.down })

        allowSyncToComplete.complete(Unit)
        runCurrent()
        forwarder.stop()
    }

    @Test
    fun failedFinalSnapshotDoesNotFallBackToLegacyEdges() = runTest {
        val syncStarted = CompletableDeferred<Unit>()
        val allowSyncToComplete = CompletableDeferred<Unit>()
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            sequencedSupported = true,
            syncStarted = syncStarted,
            allowSyncToComplete = allowSyncToComplete,
            syncResults = mutableListOf(
                PcCommandResult.Ack,
                PcCommandResult.Ack,
                PcCommandResult.Failed("Final synchronization failed.")
            )
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        runCurrent()
        syncStarted.await()

        forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 1_000L)
        runCurrent()
        allowSyncToComplete.complete(Unit)
        runCurrent()
        forwarder.stop()

        val fallbackReleases = host.commands.filterIsInstance<PcControlCommand.LegacyGridSwitchSet>()
            .filterNot { it.down }
        assertTrue(fallbackReleases.isEmpty())
    }

    @Test
    fun legacyPcKeepsAcknowledgedUnsequencedEdges() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("20", "Primary")))
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()

        forwarder.onSwitchPressed(20)
        forwarder.onSwitchReleased(20)
        runCurrent()

        val edges = host.commands.filterIsInstance<PcControlCommand.LegacyGridSwitchSet>()
        assertEquals(2, edges.size)
        assertTrue(host.realtimeCommands.isEmpty())
        assertTrue(edges.all { it.sessionId == null && it.sequence == null })
        forwarder.stop()
    }

    @Test
    fun defaultHoldToStopDurationIsFiveSeconds() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("20", "Primary")))
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)

        forwarder.startLegacyGridProfile()

        assertEquals(5_000L, forwarder.state.value.holdToStopDurationMs)
        forwarder.stop()
    }

    @Test
    fun repeatedDownFromSameSequenceDoesNotResetHoldDuration() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            holdDurationMs = 2_000L
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()

        assertTrue(forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 1_000L))
        assertTrue(forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 2_000L))
        assertTrue(
            forwarder.onSwitchReleased(
                20,
                downTimeMs = 1_000L,
                eventTimeMs = 3_000L,
                cancelled = false
            )
        )
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
        assertFalse(forwarder.state.value.active)
    }

    @Test
    fun newSequenceRecoversMissingReleaseAndUsesItsOwnHoldDuration() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            holdDurationMs = 2_000L
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()

        forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 1_000L)
        forwarder.onSwitchPressed(20, downTimeMs = 10_000L, eventTimeMs = 10_000L)
        assertTrue(forwarder.state.value.mappings.single().pressed)
        forwarder.onSwitchReleased(
            20,
            downTimeMs = 10_000L,
            eventTimeMs = 10_100L,
            cancelled = false
        )
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false),
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
        assertFalse(forwarder.state.value.mappings.single().pressed)
        assertTrue(forwarder.state.value.active)
        forwarder.stop()
    }

    @Test
    fun staleReleaseDoesNotReleaseRecoveredCurrentSequence() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("20", "Primary")))
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()

        forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 1_000L)
        forwarder.onSwitchPressed(20, downTimeMs = 2_000L, eventTimeMs = 2_000L)
        forwarder.onSwitchReleased(
            20,
            downTimeMs = 1_000L,
            eventTimeMs = 2_100L,
            cancelled = false
        )
        runCurrent()

        assertTrue(forwarder.state.value.mappings.single().pressed)
        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false),
                PcControlCommand.LegacyGridSwitchSet(1, true)
            ),
            host.commands
        )

        forwarder.onSwitchReleased(
            20,
            downTimeMs = 2_000L,
            eventTimeMs = 2_100L,
            cancelled = false
        )
        runCurrent()
        assertFalse(forwarder.state.value.mappings.single().pressed)
        forwarder.stop()
    }

    @Test
    fun cancelledLongReleaseDoesNotStopForwarding() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            holdDurationMs = 2_000L
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()

        forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 1_000L)
        forwarder.onSwitchReleased(
            20,
            downTimeMs = 1_000L,
            eventTimeMs = 4_000L,
            cancelled = true
        )
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
        assertTrue(forwarder.state.value.active)
        forwarder.stop()
    }

    @Test
    fun releaseWithoutForwardedPressDoesNotSendActivationGesture() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("20", "Primary")))
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()

        assertTrue(forwarder.onSwitchReleased(20))
        runCurrent()

        assertTrue(host.commands.isEmpty())
        forwarder.stop()
    }

    @Test
    fun unknownAndOverflowSwitchesAreConsumedWithoutForwarding() = runTest {
        val switches = (1..9).map { switchEvent(it.toString(), "Switch $it") }.toMutableList()
        val host = FakeHost(switches)
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()

        assertTrue(forwarder.onSwitchPressed(9))
        assertTrue(forwarder.onSwitchReleased(9))
        assertTrue(forwarder.onSwitchPressed(100))
        assertTrue(forwarder.onSwitchReleased(100))
        runCurrent()

        assertTrue(host.commands.isEmpty())
        forwarder.stop()
    }

    @Test
    fun holdAndReleaseSendsUpThenStopsAndRestoresScanning() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("4", "Hold")), holdDurationMs = 2_000L)
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        forwarder.onSwitchPressed(4, downTimeMs = 1_000L, eventTimeMs = 1_000L)
        runCurrent()

        forwarder.onSwitchReleased(
            4,
            downTimeMs = 1_000L,
            eventTimeMs = 3_000L,
            cancelled = false
        )
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
        assertFalse(forwarder.state.value.active)
        assertEquals(1, host.suspendCount)
        assertEquals(1, host.restoreCount)
        assertEquals(1, host.releaseCount)
    }

    @Test
    fun explicitStopReleasesEveryRemotelyHeldSwitch() = runTest {
        val host = FakeHost(
            mutableListOf(
                switchEvent("1", "One"),
                switchEvent("2", "Two")
            )
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        forwarder.onSwitchPressed(1)
        forwarder.onSwitchPressed(2)
        runCurrent()

        forwarder.stop()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(2, true),
                PcControlCommand.LegacyGridSwitchSet(1, false),
                PcControlCommand.LegacyGridSwitchSet(2, false)
            ),
            host.commands
        )
        assertEquals(1, host.restoreCount)
    }

    @Test
    fun explicitStopWaitsForInFlightDownThenReleasesIt() = runTest {
        val downStarted = CompletableDeferred<Unit>()
        val allowDownToComplete = CompletableDeferred<Unit>()
        val host = FakeHost(
            mutableListOf(switchEvent("1", "One")),
            downStarted = downStarted,
            allowDownToComplete = allowDownToComplete
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        forwarder.onSwitchPressed(1)
        downStarted.await()

        val stop = async { forwarder.stop() }
        runCurrent()
        allowDownToComplete.complete(Unit)
        stop.await()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
        assertFalse(forwarder.state.value.active)
        assertEquals(1, host.restoreCount)
    }

    @Test
    fun repeatedStopCompletesCleanupOnce() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        forwarder.onSwitchPressed(1)
        runCurrent()

        val first = async { forwarder.stop() }
        val second = async { forwarder.stop() }
        first.await()
        second.await()

        assertFalse(forwarder.state.value.active)
        assertEquals(1, host.restoreCount)
        assertEquals(1, host.releaseCount)
    }

    @Test
    fun slowTransportBurstStopsInsteadOfBuildingAnUnboundedBacklog() = runTest {
        val downStarted = CompletableDeferred<Unit>()
        val allowDownToComplete = CompletableDeferred<Unit>()
        val host = FakeHost(
            mutableListOf(switchEvent("1", "One")),
            downStarted = downStarted,
            allowDownToComplete = allowDownToComplete
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        forwarder.onSwitchPressed(1)
        downStarted.await()

        repeat(100) {
            forwarder.onSwitchReleased(1)
            forwarder.onSwitchPressed(1)
        }

        assertFalse(forwarder.state.value.active)
        allowDownToComplete.complete(Unit)
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
        assertEquals(1, host.restoreCount)
    }

    @Test
    fun destroyReleasesConnectionWithoutRestartingScanning() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()

        forwarder.destroy()

        assertFalse(forwarder.state.value.active)
        assertEquals(0, host.restoreCount)
        assertEquals(1, host.releaseCount)
    }

    @Test
    fun destroySurvivesCancellationOfItsCreatingScope() = runTest {
        val parentJob = SupervisorJob()
        val parentScope = CoroutineScope(parentJob + StandardTestDispatcher(testScheduler))
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = PcSwitchControlForwarder(host, parentScope)
        forwarder.startLegacyGridProfile()
        forwarder.onSwitchPressed(1)
        runCurrent()

        parentJob.cancel()
        forwarder.destroy()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
        assertFalse(forwarder.state.value.active)
        assertEquals(1, host.releaseCount)
    }

    @Test
    fun cleanupContinuesAfterIndividualReleaseFailure() = runTest {
        val host = FakeHost(
            mutableListOf(
                switchEvent("1", "One"),
                switchEvent("2", "Two")
            ),
            throwOnReleaseIds = setOf(1)
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        forwarder.onSwitchPressed(1)
        forwarder.onSwitchPressed(2)
        runCurrent()

        forwarder.stop()

        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(2, true),
                PcControlCommand.LegacyGridSwitchSet(1, false),
                PcControlCommand.LegacyGridSwitchSet(2, false)
            ),
            host.commands
        )
        assertEquals(1, host.restoreCount)
    }

    @Test
    fun legacyReconnectKeepsHeldStateForExplicitCleanup() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        forwarder.onSwitchPressed(1)
        runCurrent()

        host.mutableConnectionState.value = PcServiceConnectionState.Reconnecting(
            session = com.enaboapps.switchify.pc.PcAuthenticatedSession("desktop", "device", "endpoint"),
            displayName = "Office PC"
        )
        runCurrent()

        assertTrue(forwarder.state.value.active)
        assertEquals(PcSwitchControlConnectionStatus.Reconnecting, forwarder.state.value.connectionStatus)
        forwarder.stop()
        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
    }

    @Test
    fun sequencedReconnectImmediatelySynchronizesCurrentPressedSet() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("1", "One")),
            sequencedSupported = true
        )
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        runCurrent()
        forwarder.onSwitchPressed(1)
        runCurrent()
        host.commands.clear()

        host.mutableConnectionState.value = PcServiceConnectionState.Reconnecting(
            session = com.enaboapps.switchify.pc.PcAuthenticatedSession("desktop", "device", "endpoint"),
            displayName = "Office PC"
        )
        runCurrent()
        host.mutableConnectionState.value = PcServiceConnectionState.Connected(
            session = com.enaboapps.switchify.pc.PcAuthenticatedSession("desktop", "device", "endpoint"),
            displayName = "Office PC",
            pointerProfile = null
        )
        runCurrent()

        val sync = host.commands.filterIsInstance<PcControlCommand.LegacyGridSwitchSync>().single()
        assertEquals(setOf(1), sync.pressedSwitchIds)
        assertTrue(forwarder.state.value.active)
        forwarder.stop()
    }

    @Test
    fun terminalFailureReleasesHeldSwitchAndRestoresScanning() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = PcSwitchControlForwarder(host, backgroundScope)
        forwarder.startLegacyGridProfile()
        forwarder.onSwitchPressed(1)
        runCurrent()

        host.mutableConnectionState.value = PcServiceConnectionState.Failed("Terminal")
        runCurrent()

        assertFalse(forwarder.state.value.active)
        assertEquals(
            listOf(
                PcControlCommand.LegacyGridSwitchSet(1, true),
                PcControlCommand.LegacyGridSwitchSet(1, false)
            ),
            host.commands
        )
        assertEquals(1, host.restoreCount)
    }

    @Test
    fun entryRequiresExternalSwitchAndCapabilityWithoutSuspendingScanning() = runTest {
        val noSwitchHost = FakeHost(mutableListOf())
        val unsupportedHost = FakeHost(
            mutableListOf(switchEvent("1", "One")),
            supported = false
        )

        assertEquals(
            PcSwitchControlStartResult.NoExternalSwitches,
            PcSwitchControlForwarder(noSwitchHost, backgroundScope).startLegacyGridProfile()
        )
        assertEquals(
            PcSwitchControlStartResult.UnsupportedPc,
            PcSwitchControlForwarder(unsupportedHost, backgroundScope).startLegacyGridProfile()
        )
        assertEquals(0, noSwitchHost.suspendCount)
        assertEquals(0, unsupportedHost.suspendCount)
    }

    @Test
    fun inactivityTimeoutStopsSessionAndEmitsTerminalExit() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = PcSwitchControlForwarder(
            host = host,
            scope = backgroundScope,
            inactivityTimeoutMs = 1_000L
        )
        val exit = CompletableDeferred<PcSwitchControlExitReason>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            exit.complete(forwarder.terminalExitEvents.first())
        }
        assertEquals(PcSwitchControlStartResult.Started, forwarder.startLegacyGridProfile())

        advanceTimeBy(999L)
        runCurrent()
        assertTrue(forwarder.state.value.active)
        advanceTimeBy(1L)
        runCurrent()

        assertFalse(forwarder.state.value.active)
        assertEquals(PcSwitchControlExitReason.InactivityTimeout, exit.await())
        assertEquals(1, host.inactivityTimeoutCount)
        assertEquals(1, host.restoreCount)
        assertEquals(1, host.releaseCount)
    }

    @Test
    fun consumedOverflowAndCancelledEventsResetInactivityTimeout() = runTest {
        val switches = (1..9).map { switchEvent(it.toString(), "Switch $it") }.toMutableList()
        val host = FakeHost(switches)
        val forwarder = PcSwitchControlForwarder(
            host = host,
            scope = backgroundScope,
            inactivityTimeoutMs = 1_000L
        )
        assertEquals(PcSwitchControlStartResult.Started, forwarder.startLegacyGridProfile())

        advanceTimeBy(900L)
        assertTrue(forwarder.onSwitchPressed(9, 9L, 900L))
        runCurrent()
        advanceTimeBy(900L)
        assertTrue(forwarder.state.value.active)
        assertTrue(forwarder.onSwitchReleased(9, 9L, 1_800L, cancelled = true))
        runCurrent()
        advanceTimeBy(999L)
        runCurrent()
        assertTrue(forwarder.state.value.active)
        advanceTimeBy(1L)
        runCurrent()

        assertFalse(forwarder.state.value.active)
        assertEquals(1, host.inactivityTimeoutCount)
    }

    @Test
    fun normalStopCancelsInactivityTimeout() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = PcSwitchControlForwarder(
            host = host,
            scope = backgroundScope,
            inactivityTimeoutMs = 1_000L
        )
        assertEquals(PcSwitchControlStartResult.Started, forwarder.startLegacyGridProfile())

        forwarder.stop()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(0, host.inactivityTimeoutCount)
    }

    @Test
    fun staleTimeoutCannotStopReplacementSession() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = PcSwitchControlForwarder(
            host = host,
            scope = backgroundScope,
            inactivityTimeoutMs = 1_000L
        )
        assertEquals(PcSwitchControlStartResult.Started, forwarder.startLegacyGridProfile())
        advanceTimeBy(800L)
        forwarder.requestChangeProfile()
        runCurrent()
        assertEquals(PcSwitchControlStartResult.Started, forwarder.startLegacyGridProfile())

        advanceTimeBy(201L)
        runCurrent()
        assertTrue(forwarder.state.value.active)
        advanceTimeBy(799L)
        runCurrent()

        assertFalse(forwarder.state.value.active)
        assertEquals(1, host.inactivityTimeoutCount)
    }

    @Test
    fun inactivityTimeoutContinuesDuringReconnect() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = PcSwitchControlForwarder(
            host = host,
            scope = backgroundScope,
            inactivityTimeoutMs = 1_000L
        )
        assertEquals(PcSwitchControlStartResult.Started, forwarder.startLegacyGridProfile())
        advanceTimeBy(500L)
        host.mutableConnectionState.value = PcServiceConnectionState.Reconnecting(
            session = com.enaboapps.switchify.pc.PcAuthenticatedSession(
                "desktop",
                "device",
                "endpoint"
            ),
            displayName = "Office PC"
        )
        runCurrent()

        advanceTimeBy(500L)
        runCurrent()

        assertFalse(forwarder.state.value.active)
        assertEquals(1, host.inactivityTimeoutCount)
    }

    private class FakeHost(
        val switches: MutableList<SwitchEvent>,
        supported: Boolean = true,
        private val sequencedSupported: Boolean = false,
        private val genericSupported: Boolean = false,
        private val holdDurationMs: Long = PcSwitchControlForwarder.DEFAULT_HOLD_TO_STOP_MS,
        private val throwOnReleaseIds: Set<Int> = emptySet(),
        private val downStarted: CompletableDeferred<Unit>? = null,
        private val allowDownToComplete: CompletableDeferred<Unit>? = null,
        private val syncStarted: CompletableDeferred<Unit>? = null,
        private val allowSyncToComplete: CompletableDeferred<Unit>? = null,
        private val syncResults: MutableList<PcCommandResult> = mutableListOf(),
        private val startStarted: CompletableDeferred<Unit>? = null,
        private val allowStartToComplete: CompletableDeferred<Unit>? = null,
        private val stopStarted: CompletableDeferred<Unit>? = null,
        private val allowStopToComplete: CompletableDeferred<Unit>? = null
    ) : PcSwitchControlHost {
        val mutableConnectionState = MutableStateFlow<PcServiceConnectionState>(
            PcServiceConnectionState.Connected(
                session = com.enaboapps.switchify.pc.PcAuthenticatedSession("desktop", "device", "endpoint"),
                displayName = "Office PC",
                pointerProfile = null
            )
        )
        override val connectionState: StateFlow<PcServiceConnectionState> = mutableConnectionState
        val commands = mutableListOf<PcControlCommand>()
        val realtimeCommands = mutableListOf<PcControlCommand>()
        var suspendCount = 0
        var restoreCount = 0
        var maintainCount = 0
        var releaseCount = 0
        var inactivityTimeoutCount = 0
        private val profile = PcPointerMovementProfile(
            displayId = "display",
            scaleFactor = 1.0,
            bounds = PcPointerBounds(0, 0, 1920, 1080),
            maxDelta = 500,
            recommendedDeltas = PcPointerDeltas(50, 100, 200),
            capabilities = PcPointerCapabilities(
                supportedCommands = if (supported) {
                    buildSet {
                        add(PcProtocol.LEGACY_GRID_SWITCH_SET_COMMAND)
                        if (sequencedSupported) add(PcProtocol.LEGACY_GRID_SWITCH_SYNC_COMMAND)
                        if (genericSupported) addAll(PcSwitchControlForwarder.GENERIC_COMMANDS)
                    }
                } else {
                    emptySet()
                },
                noAckCommands = if (sequencedSupported) {
                    setOf(PcProtocol.LEGACY_GRID_SWITCH_SET_COMMAND)
                } else {
                    emptySet()
                }
            )
        )

        override fun currentPointerProfile() = profile
        override fun currentPcName() = "Office PC"
        override suspend fun requestProfileCatalog(): PcProfileCatalogResult {
            return PcProfileCatalogResult.Loaded(
                PcSwitchProfileCatalog(
                    1,
                    listOf(
                        PcSwitchProfileSummary(
                            "builtin.keyboard",
                            1,
                            "Generic keyboard",
                            "mapped",
                            listOf(
                                PcSwitchBindingSummary(1, "Space", "stateful"),
                                PcSwitchBindingSummary(2, "Unassigned", "unassigned")
                            )
                        )
                    )
                )
            )
        }
        override fun configuredSwitches() = switches.toList()
        override fun holdToStopDurationMs() = holdDurationMs
        override fun suspendScanning() {
            suspendCount++
        }
        override fun restoreScanning() {
            restoreCount++
        }
        override fun maintainConnection() {
            maintainCount++
        }
        override fun releaseConnection() {
            releaseCount++
        }
        override fun showInactivityTimeout() {
            inactivityTimeoutCount++
        }
        override suspend fun send(command: PcControlCommand): PcCommandResult {
            commands += command
            if (command is PcControlCommand.SwitchSessionStart) {
                startStarted?.complete(Unit)
                allowStartToComplete?.await()
            }
            if (command is PcControlCommand.SwitchSessionStop) {
                stopStarted?.complete(Unit)
                allowStopToComplete?.await()
            }
            if (command is PcControlCommand.LegacyGridSwitchSync) {
                syncStarted?.complete(Unit)
                allowSyncToComplete?.await()
                if (syncResults.isNotEmpty()) {
                    return syncResults.removeAt(0)
                }
            }
            if (command is PcControlCommand.LegacyGridSwitchSet && command.down) {
                downStarted?.complete(Unit)
                allowDownToComplete?.await()
            }
            if (command is PcControlCommand.LegacyGridSwitchSet &&
                !command.down &&
                command.switchId in throwOnReleaseIds
            ) {
                throw IllegalStateException("Release failed")
            }
            return PcCommandResult.Ack
        }
        override suspend fun sendRealtime(command: PcControlCommand): PcCommandResult {
            realtimeCommands += command
            return send(command)
        }
    }

    private fun switchEvent(
        code: String,
        name: String,
        type: String = SWITCH_EVENT_TYPE_EXTERNAL
    ) = SwitchEvent(
        type = type,
        name = name,
        code = code,
        pressAction = SwitchAction(0),
        holdActions = emptyList()
    )

    private fun PcSwitchControlForwarder.onSwitchPressed(keyCode: Int): Boolean {
        val time = keyCode.toLong()
        return onSwitchPressed(keyCode, downTimeMs = time, eventTimeMs = time)
    }

    private fun PcSwitchControlForwarder.onSwitchReleased(keyCode: Int): Boolean {
        val time = keyCode.toLong()
        return onSwitchReleased(
            keyCode,
            downTimeMs = time,
            eventTimeMs = time + 1L,
            cancelled = false
        )
    }
}
