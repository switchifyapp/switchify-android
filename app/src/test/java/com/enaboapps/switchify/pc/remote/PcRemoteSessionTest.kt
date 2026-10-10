package com.enaboapps.switchify.pc.remote

import com.enaboapps.switchify.pc.protocol.PcCommands
import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcRemoteSessionTest {
    private val moveRepeat = SentCommand(
        "mouse.repeat.start",
        "{\"command\":{\"type\":\"mouse.move\",\"payload\":{\"dx\":10,\"dy\":0}}}",
        PcResponseMode.Ack
    )
    private val stop = SentCommand("mouse.repeat.stop", "{}", PcResponseMode.Ack)

    private fun keyRepeat(key: String) = SentCommand(
        "mouse.repeat.start",
        "{\"command\":{\"type\":\"keyboard.key\",\"payload\":{\"key\":\"$key\"}}}",
        PcResponseMode.Ack
    )

    private fun key(key: String) = SentCommand("keyboard.key", "{\"key\":\"$key\"}", PcResponseMode.Ack)

    private fun TestScope.session(
        sender: FakeRemoteSender,
        profile: PcPointerProfile? = remoteProfile(),
        switchStop: PcRepeatSwitchStop = PcRepeatSwitchStop.None
    ) = PcRemoteSession(sender, { profile }, this, switchStop) { "stream-1" }

    @Test
    fun cleansRepeatDragModifiersAndTyping() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.toggleDrag()
        session.toggleModifier("Shift")
        session.streamChunk("safe fixture")
        session.cleanup()
        assertEquals(
            listOf(
                "mouse.dragStart", "keyboard.modifierDown", "keyboard.textStream.open", "keyboard.textStream.chunk",
                "mouse.dragEnd", "keyboard.modifierUp", "keyboard.textStream.close"
            ),
            sender.types
        )
        assertEquals(PcResponseMode.None, sender.calls[4].mode)
        assertEquals(PcResponseMode.Ack, sender.calls[6].mode)
        assertEquals(PcRemoteSessionState(), session.snapshot())
    }

    @Test
    fun usesPcSideRepeatPayloadAndNextControlStopsIt() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.mouse(PcCommands.move(10.0, 0.0), repeatable = true)
        assertEquals("mouse.move", session.snapshot().repeat)
        assertTrue(session.mouse(PcCommands.click()))
        assertEquals(listOf(moveRepeat, stop), sender.calls)
        assertNull(session.snapshot().repeat)
    }

    @Test
    fun repeatsAllowlistedKeyAndSameKeyStopsIt() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.key("ArrowDown")
        assertEquals("keyboard.key", session.snapshot().repeat)
        session.key("ArrowDown")
        assertNull(session.snapshot().repeat)
        assertEquals(listOf(keyRepeat("ArrowDown"), stop), sender.calls)
    }

    @Test
    fun clearsKeyRepeatBeforeChangingModifier() = runTest {
        for (held in listOf(false, true)) {
            val sender = FakeRemoteSender()
            val switchStop = FakeSwitchStop()
            val session = session(sender, switchStop = switchStop)
            if (held) session.toggleModifier("Shift")
            session.key("ArrowDown")
            sender.calls.clear()

            session.toggleModifier("Shift")
            assertNull(session.snapshot().repeat)
            assertEquals(if (held) emptyList<String>() else listOf("Shift"), session.snapshot().modifiers)
            assertNull(switchStop.armed)
            session.key("ArrowDown")
            assertEquals(
                listOf(
                    stop,
                    SentCommand(if (held) "keyboard.modifierUp" else "keyboard.modifierDown", "{\"key\":\"Shift\"}", PcResponseMode.Ack),
                    keyRepeat("ArrowDown")
                ),
                sender.calls
            )
            session.cleanup()
            session.dispose()
        }
    }

    @Test
    fun ordersModifierChangeBetweenPendingRepeatStartAndNextKey() = runTest {
        val sender = FakeRemoteSender()
        val switchStop = FakeSwitchStop()
        val session = session(sender, switchStop = switchStop)
        val start = sender.gate("mouse.repeat.start")
        val jobs = listOf(
            async { session.key("ArrowDown") },
            async { session.toggleModifier("Shift") },
            async { session.key("ArrowDown") }
        )
        runCurrent()
        assertEquals(1, sender.calls.size)

        start.complete(true)
        jobs.awaitAll()
        assertEquals(
            listOf("mouse.repeat.start", "mouse.repeat.stop", "keyboard.modifierDown", "mouse.repeat.start"),
            sender.types
        )
        assertEquals(listOf("Shift"), session.snapshot().modifiers)
        assertEquals(2, switchStop.armCount)
        assertTrue(switchStop.armed != null)
        session.cleanup()
        session.dispose()
    }

    @Test
    fun clearsRepeatWhenExistingStreamSendsCommands() = runTest {
        for (operation in listOf("chunk", "key", "close")) {
            val sender = FakeRemoteSender()
            val switchStop = FakeSwitchStop()
            val session = session(sender, switchStop = switchStop)
            session.streamChunk("a")
            session.key("ArrowDown")
            sender.calls.clear()

            when (operation) {
                "chunk" -> session.streamChunk("b")
                "key" -> session.streamKey("Enter")
                else -> session.closeStream()
            }

            assertNull(session.snapshot().repeat)
            assertNull(switchStop.armed)
            assertEquals(stop, sender.calls[0])
            assertEquals("keyboard.textStream.$operation", sender.calls[1].type)
            val expected = if (operation == "close") "\"expectedCount\":1" else "\"seq\":1"
            assertTrue(sender.calls[1].payload.contains("\"streamId\":\"stream-1\""))
            assertTrue(sender.calls[1].payload.contains(expected))
            session.key("ArrowDown")
            assertEquals(keyRepeat("ArrowDown"), sender.calls.last())
            session.cleanup()
            session.dispose()
        }
    }

    @Test
    fun stopsPendingRepeatBeforeResumingExistingStream() = runTest {
        val sender = FakeRemoteSender()
        val switchStop = FakeSwitchStop()
        val session = session(sender, switchStop = switchStop)
        session.streamChunk("a")
        sender.calls.clear()
        val start = sender.gate("mouse.repeat.start")
        val starting = async { session.key("ArrowDown") }
        runCurrent()
        val typing = async { session.streamChunk("b") }
        runCurrent()
        assertEquals(1, sender.calls.size)
        start.complete(true)
        starting.await()
        typing.await()
        assertEquals(listOf("mouse.repeat.start", "mouse.repeat.stop", "keyboard.textStream.chunk"), sender.types)
        assertNull(session.snapshot().repeat)
        assertNull(switchStop.armed)
    }

    @Test
    fun fallsBackToSingleKeyPressWhenDesktopCannotRepeatThatKey() = runTest {
        val cases = listOf(
            remoteProfile(keyRepeat = false) to "ArrowDown",
            remoteProfile(keyRepeatEnabled = false, repeatableKeys = repeatableFixtureKeys) to "ArrowDown",
            remoteProfile() to "Enter"
        )
        for ((profile, pressed) in cases) {
            val sender = FakeRemoteSender()
            val session = session(sender, profile)
            session.key(pressed)
            assertEquals(listOf(key(pressed)), sender.calls)
            assertNull(session.snapshot().repeat)
        }
    }

    @Test
    fun deliversNonRepeatableKeyThatStopsActiveRepeat() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.key("ArrowDown")
        session.key("Escape")
        assertNull(session.snapshot().repeat)
        assertEquals(listOf(keyRepeat("ArrowDown"), stop, key("Escape")), sender.calls)
    }

    @Test
    fun deliversKeyPressedDuringPointerRepeatOnDesktopWithoutKeyRepeat() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender, remoteProfile(keyRepeat = false))
        session.mouse(PcCommands.move(10.0, 0.0), repeatable = true)
        session.key("ArrowDown")
        assertNull(session.snapshot().repeat)
        assertEquals(listOf(moveRepeat, stop, key("ArrowDown")), sender.calls)
    }

    @Test
    fun refusesToRepeatKeysOutsideClientAllowlist() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender, remoteProfile(repeatableKeys = listOf("Enter", "a", "Escape", "ArrowDown")))
        for (pressed in listOf("Enter", "a", "Escape")) {
            session.key(pressed)
            assertNull(session.snapshot().repeat)
        }
        assertEquals(listOf(key("Enter"), key("a"), key("Escape")), sender.calls)
        session.key("ArrowDown")
        assertEquals("keyboard.key", session.snapshot().repeat)
    }

    @Test
    fun fallsBackToSingleKeyPressWithoutRepeatCommands() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender, remoteProfile(allRemoteCommands.filterNot { it.startsWith("mouse.repeat.") }))
        session.key("ArrowDown")
        assertEquals(listOf(key("ArrowDown")), sender.calls)
    }

    @Test
    fun doesNotSendUnsupportedKey() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender, remoteProfile(allRemoteCommands.filterNot { it == "keyboard.key" }))
        assertFalse(session.key("ArrowDown"))
        assertTrue(sender.calls.isEmpty())
    }

    @Test
    fun physicalSwitchStopsRepeatingKeyAndCleanupStopsNext() = runTest {
        val sender = FakeRemoteSender()
        val switchStop = FakeSwitchStop()
        val session = session(sender, switchStop = switchStop)
        session.key("Backspace")
        assertEquals(1, switchStop.armCount)
        assertTrue(switchStop.press())
        runCurrent()
        assertNull(session.snapshot().repeat)
        assertEquals(listOf("mouse.repeat.start", "mouse.repeat.stop"), sender.types)
        assertFalse(switchStop.press())

        session.key("Tab")
        session.cleanup()
        assertNull(session.snapshot().repeat)
        assertEquals(listOf("mouse.repeat.start", "mouse.repeat.stop", "mouse.repeat.start", "mouse.repeat.stop"), sender.types)
        assertNull(switchStop.armed)
    }

    @Test
    fun staleSwitchStopDoesNotStopNewerRepeat() = runTest {
        val sender = FakeRemoteSender()
        val switchStop = FakeSwitchStop()
        val session = session(sender, switchStop = switchStop)
        session.mouse(PcCommands.move(10.0, 0.0), repeatable = true)
        val stale = switchStop.armed!!
        session.stopRepeat()
        session.mouse(PcCommands.move(-10.0, 0.0), repeatable = true)
        stale()
        runCurrent()
        assertEquals("mouse.move", session.snapshot().repeat)
        assertTrue(switchStop.press())
        runCurrent()
        assertNull(session.snapshot().repeat)
        assertEquals(
            listOf("mouse.repeat.start", "mouse.repeat.stop", "mouse.repeat.start", "mouse.repeat.stop"),
            sender.types
        )
    }

    @Test
    fun stopsPointerRepeatWithKeyAndStillDeliversKey() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.mouse(PcCommands.move(10.0, 0.0), repeatable = true)
        session.key("ArrowDown")
        assertNull(session.snapshot().repeat)
        assertEquals(listOf("mouse.repeat.start", "mouse.repeat.stop", "keyboard.key"), sender.types)

        session.key("ArrowDown")
        session.mouse(PcCommands.click())
        assertNull(session.snapshot().repeat)
        assertEquals(
            listOf("mouse.repeat.start", "mouse.repeat.stop", "keyboard.key", "mouse.repeat.start", "mouse.repeat.stop"),
            sender.types
        )
    }

    @Test
    fun onlyTheRepeatingKeyIsItsOwnToggle() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.key("ArrowDown")
        session.key("ArrowUp")
        assertNull(session.snapshot().repeat)
        assertEquals(listOf(keyRepeat("ArrowDown"), stop, key("ArrowUp")), sender.calls)
    }

    @Test
    fun clearsRepeatImmediatelyAndSendsOneStopForDuplicates() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.mouse(PcCommands.move(10.0, 0.0), repeatable = true)
        val stopGate = sender.gate("mouse.repeat.stop")
        val first = async { session.stopRepeat() }
        val duplicate = async { session.stopRepeat() }
        runCurrent()
        assertNull(session.snapshot().repeat)
        assertEquals(listOf("mouse.repeat.start", "mouse.repeat.stop"), sender.types)
        stopGate.complete(false)
        first.await()
        duplicate.await()
        assertNull(session.snapshot().repeat)
        assertEquals(listOf("mouse.repeat.start", "mouse.repeat.stop"), sender.types)
    }

    @Test
    fun keepsRepeatClearedWhenStopFails() = runTest {
        val sender = FakeRemoteSender()
        sender.result = { it.type != "mouse.repeat.stop" }
        val session = session(sender)
        session.mouse(PcCommands.move(10.0, 0.0), repeatable = true)
        session.stopRepeat()
        assertNull(session.snapshot().repeat)
        assertEquals(listOf(moveRepeat, stop), sender.calls)
    }

    @Test
    fun stopsOldSessionBeforeReplacementStartsRepeating() = runTest {
        val sender = FakeRemoteSender()
        val old = session(sender)
        old.mouse(PcCommands.move(10.0, 0.0), repeatable = true)
        val replacement = session(sender)
        old.cleanup()
        replacement.mouse(PcCommands.move(-10.0, 0.0), repeatable = true)
        assertEquals(listOf("mouse.repeat.start", "mouse.repeat.stop", "mouse.repeat.start"), sender.types)
        assertEquals(PcResponseMode.Ack, sender.calls[1].mode)
        assertNull(old.snapshot().repeat)
        assertEquals("mouse.move", replacement.snapshot().repeat)
    }

    @Test
    fun repeatsScrollAndFallsBackWhenMouseRepeatDisabled() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.mouse(PcCommands.scroll(0.0, 5.0), repeatable = true)
        assertEquals("mouse.scroll", session.snapshot().repeat)
        assertEquals("{\"command\":{\"type\":\"mouse.scroll\",\"payload\":{\"dx\":0,\"dy\":5}}}", sender.calls[0].payload)

        val plain = FakeRemoteSender()
        val disabled = session(plain, remoteProfile(mouseRepeat = false))
        disabled.mouse(PcCommands.move(10.0, 0.0), repeatable = true)
        assertNull(disabled.snapshot().repeat)
        assertEquals(listOf(SentCommand("mouse.move", "{\"dx\":10,\"dy\":0}", PcResponseMode.Ack)), plain.calls)
    }

    @Test
    fun serializesStreamOpenAndAdvancesSequenceNumbers() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        val open = sender.gate("keyboard.textStream.open")
        val first = async { session.streamChunk("a") }
        val second = async { session.streamChunk("b") }
        runCurrent()
        assertEquals(listOf("keyboard.textStream.open"), sender.types)
        open.complete(true)
        first.await()
        second.await()
        assertEquals(listOf("keyboard.textStream.open", "keyboard.textStream.chunk", "keyboard.textStream.chunk"), sender.types)
        assertTrue(sender.calls[1].payload.contains("\"seq\":0"))
        assertTrue(sender.calls[2].payload.contains("\"seq\":1"))
    }

    @Test
    fun neverSendsCapabilitiesThePcDidNotAdvertise() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender, remoteProfile(listOf("mouse.move")))
        assertFalse(session.command(PcCommands.click()))
        assertFalse(session.streamChunk("not sent"))
        assertTrue(session.mouse(PcCommands.move(1.0, 0.0)))
        assertEquals(1, sender.calls.size)
    }

    @Test
    fun usesSafeDisabledDefaultsWithoutProfile() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender, null)
        assertFalse(session.supports("mouse.move"))
        assertFalse(session.command(PcCommands.click()))
        assertFalse(session.streamKey("Enter"))
        assertTrue(sender.calls.isEmpty())
    }

    @Test
    fun releasesModifiersAfterSuccessfulShortcutOnly() = runTest {
        val sender = FakeRemoteSender()
        var shortcutSucceeds = false
        sender.result = { it.type != "keyboard.shortcut" || shortcutSucceeds }
        val session = session(sender)
        session.toggleModifier("Ctrl")
        assertFalse(session.shortcut("C"))
        assertEquals(listOf("Ctrl"), session.snapshot().modifiers)
        shortcutSucceeds = true
        assertTrue(session.shortcut("C"))
        assertTrue(session.snapshot().modifiers.isEmpty())
        assertEquals(listOf("keyboard.modifierDown", "keyboard.shortcut", "keyboard.shortcut", "keyboard.modifierUp"), sender.types)
        assertEquals("{\"keys\":[\"Ctrl\",\"C\"]}", sender.calls[2].payload)
    }

    @Test
    fun cleanupEndsDragStartedJustBeforeIt() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        val dragAck = sender.gate("mouse.dragStart")
        val drag = async { session.toggleDrag() }
        runCurrent()
        val cleanup = async { session.cleanup() }
        runCurrent()
        assertEquals(listOf("mouse.dragStart"), sender.types)
        dragAck.complete(true)
        drag.await()
        cleanup.await()
        assertEquals(listOf("mouse.dragStart", "mouse.dragEnd"), sender.types)
        assertFalse(session.snapshot().dragging)
    }

    @Test
    fun cleanupStopsRepeatStartedJustBeforeItAndReleasesSwitchStop() = runTest {
        val sender = FakeRemoteSender()
        val switchStop = FakeSwitchStop()
        val session = session(sender, switchStop = switchStop)
        val startAck = sender.gate("mouse.repeat.start")
        val start = async { session.mouse(PcCommands.move(10.0, 0.0), repeatable = true) }
        runCurrent()
        val cleanup = async { session.cleanup() }
        runCurrent()
        startAck.complete(true)
        start.await()
        cleanup.await()
        assertEquals(listOf(moveRepeat, stop), sender.calls)
        assertNull(session.snapshot().repeat)
        assertNull(switchStop.armed)
        assertEquals(1, switchStop.releaseCount)
    }

    @Test
    fun actionsRequestedBeforeCleanupRunsAreDroppedAndLaterActionsWork() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        val clickAck = sender.gate("mouse.click")
        val click = async { session.command(PcCommands.click()) }
        runCurrent()
        val drag = async { session.toggleDrag() }
        val repeat = async { session.key("ArrowDown") }
        val modifier = async { session.toggleModifier("Shift") }
        val typing = async { session.streamChunk("late") }
        runCurrent()
        val cleanup = async { session.cleanup() }
        runCurrent()
        clickAck.complete(true)
        click.await()
        assertFalse(drag.await())
        assertFalse(repeat.await())
        assertFalse(modifier.await())
        assertFalse(typing.await())
        cleanup.await()
        assertEquals(listOf("mouse.click"), sender.types)
        assertEquals(PcRemoteSessionState(), session.snapshot())

        assertTrue(session.toggleDrag())
        assertEquals("mouse.dragStart", sender.types.last())
    }

    @Test
    fun closedSessionSendsNothingFurther() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.toggleModifier("Ctrl")
        session.close()
        assertFalse(session.key("ArrowDown"))
        assertFalse(session.toggleDrag())
        assertEquals(listOf("keyboard.modifierDown", "keyboard.modifierUp"), sender.types)
    }

    @Test
    fun closeStreamUsesAcknowledgedModeThePcAccepts() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender)
        session.streamChunk("a")
        session.closeStream()
        assertEquals("keyboard.textStream.close", sender.calls.last().type)
        assertEquals(PcResponseMode.Ack, sender.calls.last().mode)
        assertFalse(session.snapshot().streamOpen)
    }

    @Test
    fun clickEndsDraggingAndAdvertisedNoAckCommandsSkipAcknowledgement() = runTest {
        val sender = FakeRemoteSender()
        val session = session(sender, remoteProfile(noAckCommands = listOf("mouse.click", "keyboard.key")))
        session.toggleDrag()
        assertTrue(session.snapshot().dragging)
        session.command(PcCommands.click())
        assertFalse(session.snapshot().dragging)
        session.key("Enter")
        assertEquals(PcResponseMode.Ack, sender.calls[0].mode)
        assertEquals(PcResponseMode.None, sender.calls[1].mode)
        assertEquals(PcResponseMode.None, sender.calls[2].mode)
    }
}
