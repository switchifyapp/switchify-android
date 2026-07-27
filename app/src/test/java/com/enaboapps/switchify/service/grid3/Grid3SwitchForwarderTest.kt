package com.enaboapps.switchify.service.grid3

import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcPointerBounds
import com.enaboapps.switchify.pc.PcPointerCapabilities
import com.enaboapps.switchify.pc.PcPointerDeltas
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcProtocol
import com.enaboapps.switchify.pc.PcServiceConnectionState
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Grid3SwitchForwarderTest {
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)

        assertEquals(Grid3StartResult.Started, forwarder.start())
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

        assertEquals(listOf(PcControlCommand.GridSwitchSet(1, true)), host.commands)
        forwarder.stop()
    }

    @Test
    fun forwardsOrderedDeduplicatedDownAndUpWithLiveFeedback() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("20", "Primary")))
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()

        assertTrue(forwarder.onSwitchPressed(20))
        assertTrue(forwarder.state.value.mappings.single().pressed)
        assertTrue(forwarder.onSwitchPressed(20))
        assertTrue(forwarder.onSwitchReleased(20))
        assertFalse(forwarder.state.value.mappings.single().pressed)
        assertTrue(forwarder.onSwitchReleased(20))
        runCurrent()

        assertEquals(
            listOf(
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
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

        val edges = host.realtimeCommands.filterIsInstance<PcControlCommand.GridSwitchSet>()
        assertEquals(2, edges.size)
        assertTrue(edges.first().down)
        assertFalse(edges.last().down)
        assertEquals(edges.first().sessionId, edges.last().sessionId)
        assertTrue(requireNotNull(edges.first().sequence) < requireNotNull(edges.last().sequence))

        advanceTimeBy(Grid3SwitchForwarder.SNAPSHOT_INTERVAL_MS)
        runCurrent()

        val sync = host.commands.filterIsInstance<PcControlCommand.GridSwitchSync>().last()
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
        runCurrent()
        syncStarted.await()

        forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 1_000L)
        forwarder.onSwitchReleased(20, downTimeMs = 1_000L, eventTimeMs = 1_100L, cancelled = false)
        runCurrent()

        val edges = host.realtimeCommands.filterIsInstance<PcControlCommand.GridSwitchSet>()
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
        runCurrent()
        syncStarted.await()

        forwarder.onSwitchPressed(20, downTimeMs = 1_000L, eventTimeMs = 1_000L)
        runCurrent()
        allowSyncToComplete.complete(Unit)
        runCurrent()
        forwarder.stop()

        val fallbackReleases = host.commands.filterIsInstance<PcControlCommand.GridSwitchSet>()
            .filterNot { it.down }
        assertTrue(fallbackReleases.isEmpty())
    }

    @Test
    fun legacyPcKeepsAcknowledgedUnsequencedEdges() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("20", "Primary")))
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()

        forwarder.onSwitchPressed(20)
        forwarder.onSwitchReleased(20)
        runCurrent()

        val edges = host.commands.filterIsInstance<PcControlCommand.GridSwitchSet>()
        assertEquals(2, edges.size)
        assertTrue(host.realtimeCommands.isEmpty())
        assertTrue(edges.all { it.sessionId == null && it.sequence == null })
        forwarder.stop()
    }

    @Test
    fun defaultHoldToStopDurationIsFiveSeconds() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("20", "Primary")))
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)

        forwarder.start()

        assertEquals(5_000L, forwarder.state.value.holdToStopDurationMs)
        forwarder.stop()
    }

    @Test
    fun repeatedDownFromSameSequenceDoesNotResetHoldDuration() = runTest {
        val host = FakeHost(
            mutableListOf(switchEvent("20", "Primary")),
            holdDurationMs = 2_000L
        )
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()

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
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()

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
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false),
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()

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
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false),
                PcControlCommand.GridSwitchSet(1, true)
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()

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
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
            ),
            host.commands
        )
        assertTrue(forwarder.state.value.active)
        forwarder.stop()
    }

    @Test
    fun releaseWithoutForwardedPressDoesNotSendActivationGesture() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("20", "Primary")))
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()

        assertTrue(forwarder.onSwitchReleased(20))
        runCurrent()

        assertTrue(host.commands.isEmpty())
        forwarder.stop()
    }

    @Test
    fun unknownAndOverflowSwitchesAreConsumedWithoutForwarding() = runTest {
        val switches = (1..9).map { switchEvent(it.toString(), "Switch $it") }.toMutableList()
        val host = FakeHost(switches)
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()

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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
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
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
        forwarder.onSwitchPressed(1)
        forwarder.onSwitchPressed(2)
        runCurrent()

        forwarder.stop()

        assertEquals(
            listOf(
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(2, true),
                PcControlCommand.GridSwitchSet(1, false),
                PcControlCommand.GridSwitchSet(2, false)
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
        forwarder.onSwitchPressed(1)
        downStarted.await()

        val stop = async { forwarder.stop() }
        runCurrent()
        allowDownToComplete.complete(Unit)
        stop.await()

        assertEquals(
            listOf(
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
            ),
            host.commands
        )
        assertFalse(forwarder.state.value.active)
        assertEquals(1, host.restoreCount)
    }

    @Test
    fun repeatedStopCompletesCleanupOnce() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
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
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
            ),
            host.commands
        )
        assertEquals(1, host.restoreCount)
    }

    @Test
    fun destroyReleasesConnectionWithoutRestartingScanning() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()

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
        val forwarder = Grid3SwitchForwarder(host, parentScope)
        forwarder.start()
        forwarder.onSwitchPressed(1)
        runCurrent()

        parentJob.cancel()
        forwarder.destroy()

        assertEquals(
            listOf(
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
        forwarder.onSwitchPressed(1)
        forwarder.onSwitchPressed(2)
        runCurrent()

        forwarder.stop()

        assertEquals(
            listOf(
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(2, true),
                PcControlCommand.GridSwitchSet(1, false),
                PcControlCommand.GridSwitchSet(2, false)
            ),
            host.commands
        )
        assertEquals(1, host.restoreCount)
    }

    @Test
    fun legacyReconnectKeepsHeldStateForExplicitCleanup() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
        forwarder.onSwitchPressed(1)
        runCurrent()

        host.mutableConnectionState.value = PcServiceConnectionState.Reconnecting(
            session = com.enaboapps.switchify.pc.PcAuthenticatedSession("desktop", "device", "endpoint"),
            displayName = "Office PC"
        )
        runCurrent()

        assertTrue(forwarder.state.value.active)
        assertEquals(Grid3ConnectionStatus.Reconnecting, forwarder.state.value.connectionStatus)
        forwarder.stop()
        assertEquals(
            listOf(
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
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
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
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

        val sync = host.commands.filterIsInstance<PcControlCommand.GridSwitchSync>().single()
        assertEquals(setOf(1), sync.pressedSwitchIds)
        assertTrue(forwarder.state.value.active)
        forwarder.stop()
    }

    @Test
    fun terminalFailureReleasesHeldSwitchAndRestoresScanning() = runTest {
        val host = FakeHost(mutableListOf(switchEvent("1", "One")))
        val forwarder = Grid3SwitchForwarder(host, backgroundScope)
        forwarder.start()
        forwarder.onSwitchPressed(1)
        runCurrent()

        host.mutableConnectionState.value = PcServiceConnectionState.Failed("Terminal")
        runCurrent()

        assertFalse(forwarder.state.value.active)
        assertEquals(
            listOf(
                PcControlCommand.GridSwitchSet(1, true),
                PcControlCommand.GridSwitchSet(1, false)
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
            Grid3StartResult.NoExternalSwitches,
            Grid3SwitchForwarder(noSwitchHost, backgroundScope).start()
        )
        assertEquals(
            Grid3StartResult.UnsupportedPc,
            Grid3SwitchForwarder(unsupportedHost, backgroundScope).start()
        )
        assertEquals(0, noSwitchHost.suspendCount)
        assertEquals(0, unsupportedHost.suspendCount)
    }

    private class FakeHost(
        val switches: MutableList<SwitchEvent>,
        supported: Boolean = true,
        private val sequencedSupported: Boolean = false,
        private val holdDurationMs: Long = Grid3SwitchForwarder.DEFAULT_HOLD_TO_STOP_MS,
        private val throwOnReleaseIds: Set<Int> = emptySet(),
        private val downStarted: CompletableDeferred<Unit>? = null,
        private val allowDownToComplete: CompletableDeferred<Unit>? = null,
        private val syncStarted: CompletableDeferred<Unit>? = null,
        private val allowSyncToComplete: CompletableDeferred<Unit>? = null,
        private val syncResults: MutableList<PcCommandResult> = mutableListOf()
    ) : Grid3ForwardingHost {
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
        private val profile = PcPointerMovementProfile(
            displayId = "display",
            scaleFactor = 1.0,
            bounds = PcPointerBounds(0, 0, 1920, 1080),
            maxDelta = 500,
            recommendedDeltas = PcPointerDeltas(50, 100, 200),
            capabilities = PcPointerCapabilities(
                supportedCommands = if (supported) {
                    buildSet {
                        add(PcProtocol.GRID_SWITCH_SET_COMMAND)
                        if (sequencedSupported) add(PcProtocol.GRID_SWITCH_SYNC_COMMAND)
                    }
                } else {
                    emptySet()
                },
                noAckCommands = if (sequencedSupported) {
                    setOf(PcProtocol.GRID_SWITCH_SET_COMMAND)
                } else {
                    emptySet()
                }
            )
        )

        override fun currentPointerProfile() = profile
        override fun currentPcName() = "Office PC"
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
        override suspend fun send(command: PcControlCommand): PcCommandResult {
            commands += command
            if (command is PcControlCommand.GridSwitchSync) {
                syncStarted?.complete(Unit)
                allowSyncToComplete?.await()
                if (syncResults.isNotEmpty()) {
                    return syncResults.removeAt(0)
                }
            }
            if (command is PcControlCommand.GridSwitchSet && command.down) {
                downStarted?.complete(Unit)
                allowDownToComplete?.await()
            }
            if (command is PcControlCommand.GridSwitchSet &&
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

    private fun Grid3SwitchForwarder.onSwitchPressed(keyCode: Int): Boolean {
        val time = keyCode.toLong()
        return onSwitchPressed(keyCode, downTimeMs = time, eventTimeMs = time)
    }

    private fun Grid3SwitchForwarder.onSwitchReleased(keyCode: Int): Boolean {
        val time = keyCode.toLong()
        return onSwitchReleased(
            keyCode,
            downTimeMs = time,
            eventTimeMs = time + 1L,
            cancelled = false
        )
    }
}
