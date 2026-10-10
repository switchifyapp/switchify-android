package com.enaboapps.switchify.pc.remote

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.connection.PcConnectionFailure
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcProfileStatus
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.pc.transport.PcDiscoveredDesktop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcLiveTypingModelTest {
    @Test
    fun typedNewlineSubmitsLineWithoutSendingNewlineText() = runTest {
        val sender = FakeRemoteSender()
        val session = PcRemoteSession(sender, { remoteProfile() }, this) { "stream-1" }
        val model = PcLiveTypingModel(session, this)
        model.change("hi")
        advanceUntilIdle()
        model.change("hi\n")
        advanceUntilIdle()
        assertEquals("", model.text.value)
        assertNull(model.failure.value)
        assertFalse(model.submitting.value)
        assertEquals(
            listOf("keyboard.textStream.open", "keyboard.textStream.chunk", "keyboard.textStream.key"),
            sender.types
        )
        assertTrue(sender.calls[2].payload.contains("\"key\":\"Enter\""))
    }

    @Test
    fun ignoresTypingWhileEnterIsSendingSoTheFieldCanStayEditable() = runTest {
        val sender = FakeRemoteSender()
        val session = PcRemoteSession(sender, { remoteProfile() }, this) { "stream-1" }
        val model = PcLiveTypingModel(session, this)
        model.change("hi")
        advanceUntilIdle()
        val enter = sender.gate("keyboard.textStream.key")
        model.change("hi\n")
        assertTrue(model.submitting.value)
        model.change("hix")
        assertEquals("hi", model.text.value)
        enter.complete(true)
        advanceUntilIdle()
        assertEquals("", model.text.value)
        assertEquals(1, sender.types.count { it == "keyboard.textStream.chunk" })
    }

    @Test
    fun reportsUnsentTextAndRetries() = runTest {
        val sender = FakeRemoteSender()
        var fail = true
        sender.result = { !(fail && it.type == "keyboard.textStream.chunk") }
        val session = PcRemoteSession(sender, { remoteProfile() }, this) { "stream-1" }
        val model = PcLiveTypingModel(session, this)
        model.change("abc")
        advanceUntilIdle()
        assertEquals(PcLiveTypingFailure.Text, model.failure.value)
        fail = false
        model.retryText()
        advanceUntilIdle()
        assertNull(model.failure.value)
    }

    @Test
    fun pastedNewlinesAreTypedAsText() = runTest {
        val sender = FakeRemoteSender()
        val session = PcRemoteSession(sender, { remoteProfile() }, this) { "stream-1" }
        val model = PcLiveTypingModel(session, this)
        model.change("first\nsecond")
        advanceUntilIdle()
        assertEquals("first\nsecond", model.text.value)
        assertEquals(listOf("keyboard.textStream.open", "keyboard.textStream.chunk"), sender.types)
        assertTrue(sender.calls[1].payload.contains("first\\nsecond"))
    }

    @Test
    fun textLimitNeverSplitsSurrogatePair() {
        val emoji = "🙂"
        val text = "a".repeat(1_999) + emoji
        assertEquals("a".repeat(1_999), PcTextLimit.limit(text))
        assertEquals("a".repeat(1_998) + emoji, PcTextLimit.limit("a".repeat(1_998) + emoji + "b"))
        assertEquals("short", PcTextLimit.limit("short"))
    }

    @Test
    fun limitsLiveTextToPcMaximum() = runTest {
        val session = PcRemoteSession(FakeRemoteSender(), { remoteProfile() }, this) { "stream-1" }
        val model = PcLiveTypingModel(session, this)
        model.change("a".repeat(2_500))
        advanceUntilIdle()
        assertEquals(2_000, model.text.value.length)
    }

    @Test
    fun presentsDisconnectedStatesLikeRemote() {
        val office = PcDiscoveredDesktop("desktop-1", "Office", PcPlatform.Windows, null, "ble-1", -40)
        val saved = listOf(PcSavedPc("desktop-1", "Office", PcPlatform.Windows, "ble-1", 1))

        val connecting = PcRemotePresentation.disconnected(PcConnectionState.Connecting(office))
        assertTrue(connecting.busy)
        assertFalse(connecting.retry)
        assertNull(connecting.choose)
        assertEquals(PcText.Res(R.string.pc_remote_connecting_to, listOf("Office")), connecting.message)

        val reconnecting = PcRemotePresentation.disconnected(PcConnectionState.Reconnecting(office, 2))
        assertEquals(PcText.Res(R.string.pc_remote_reconnecting_to, listOf("Office", 2)), reconnecting.message)

        val empty = PcRemotePresentation.disconnected(PcConnectionState.Idle(emptyList()))
        assertEquals(R.string.pc_remote_title_no_saved, empty.titleRes)
        assertFalse(empty.retry)
        assertEquals(PcDisconnectedChooseAction.FindPc, empty.choose)

        val failed = PcRemotePresentation.disconnected(PcConnectionState.Failed(PcConnectionFailure.ConnectionLost, saved))
        assertEquals(R.string.pc_remote_title_could_not_connect, failed.titleRes)
        assertEquals(PcText.Res(R.string.pc_failure_connection_lost), failed.message)
        assertTrue(failed.retry)
        assertEquals(PcDisconnectedChooseAction.ChooseAnother, failed.choose)

        assertEquals(R.string.pc_remote_profile_restored_announcement, PcRemotePresentation.profileAnnouncement(PcProfileStatus.Ready, PcProfileStatus.Recovering))
        assertNull(PcRemotePresentation.profileAnnouncement(PcProfileStatus.Ready, null))
        assertEquals(R.string.pc_repeat_stop_repeating, PcRemotePresentation.repeatStopLabel("keyboard.key"))
        assertEquals(R.string.pc_repeat_stop_movement, PcRemotePresentation.repeatStopLabel("mouse.scroll"))
    }
}
