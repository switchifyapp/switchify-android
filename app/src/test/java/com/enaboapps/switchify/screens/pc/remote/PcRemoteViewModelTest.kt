package com.enaboapps.switchify.screens.pc.remote

import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcProfileStatus
import com.enaboapps.switchify.pc.connection.PcSendOutcome
import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcCommands
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import com.enaboapps.switchify.pc.remote.FakeRemoteSender
import com.enaboapps.switchify.pc.remote.FakeSwitchStop
import com.enaboapps.switchify.pc.remote.InMemoryPcRemotePreferences
import com.enaboapps.switchify.pc.remote.PcRemoteConnection
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.pc.remote.layouts.InMemoryPcLayoutStore
import com.enaboapps.switchify.pc.remote.remoteProfile
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.pc.transport.PcDiscoveredDesktop
import com.enaboapps.switchify.pc.transport.PcUnsubscribe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRemoteConnection : PcRemoteConnection {
    val sender = FakeRemoteSender()
    val cleanups = mutableListOf<suspend () -> Unit>()
    var connectCalls = 0

    override val state = MutableStateFlow<PcConnectionState>(PcConnectionState.Idle(emptyList()))

    override suspend fun send(command: PcCommand, responseMode: PcResponseMode) =
        sender.send(command, responseMode) == PcSendOutcome.Accepted

    override suspend fun sendWithOutcome(command: PcCommand, responseMode: PcResponseMode) = sender.send(command, responseMode)

    override fun registerCleanup(cleanup: suspend () -> Unit): PcUnsubscribe {
        cleanups += cleanup
        return PcUnsubscribe { cleanups -= cleanup }
    }

    override suspend fun connectPreferred() {
        connectCalls += 1
    }

    override suspend fun listSaved(): List<PcSavedPc> = emptyList()

    override suspend fun switchSaved(pc: PcSavedPc) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class PcRemoteViewModelTest {
    private val office = PcDiscoveredDesktop("desktop-1", "Office", PcPlatform.Windows, null, "ble-1", -40)

    private fun viewModelTest(block: suspend TestScope.(FakeRemoteConnection, PcRemoteViewModel, FakeSwitchStop) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val sessionScope = CoroutineScope(SupervisorJob() + dispatcher)
        val connection = FakeRemoteConnection()
        val switchStop = FakeSwitchStop()
        val viewModel = PcRemoteViewModel(
            connection,
            InMemoryPcRemotePreferences(),
            InMemoryPcLayoutStore(),
            switchStop,
            sessionScope
        )
        try {
            connection.state.value = PcConnectionState.Connected(office, remoteProfile(), PcProfileStatus.Ready)
            advanceUntilIdle()
            block(connection, viewModel, switchStop)
        } finally {
            sessionScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private suspend fun TestScope.holdEverything(viewModel: PcRemoteViewModel) {
        val session = viewModel.holder.value!!.session
        session.toggleDrag()
        session.toggleModifier("Shift")
        session.streamChunk("hello")
        session.mouse(PcCommands.move(10.0, 0.0), repeatable = true)
        advanceUntilIdle()
    }

    @Test
    fun hidingReleasesHeldInputWithoutTouchingTheConnection() = viewModelTest { connection, viewModel, switchStop ->
        holdEverything(viewModel)
        assertNotNull(switchStop.armed)
        connection.sender.calls.clear()

        viewModel.onHidden(changingConfigurations = false)
        advanceUntilIdle()

        assertEquals(
            listOf("mouse.repeat.stop", "mouse.dragEnd", "keyboard.modifierUp", "keyboard.textStream.close"),
            connection.sender.types
        )
        val state = viewModel.holder.value!!.session.snapshot()
        assertNull(state.repeat)
        assertFalse(state.dragging)
        assertEquals(emptyList<String>(), state.modifiers)
        assertFalse(state.streamOpen)
        assertNull(switchStop.armed)
        assertEquals(0, connection.connectCalls)
        assertNotNull(viewModel.holder.value)
    }

    @Test
    fun configurationChangeKeepsHeldInput() = viewModelTest { connection, viewModel, _ ->
        holdEverything(viewModel)
        connection.sender.calls.clear()

        viewModel.onHidden(changingConfigurations = true)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), connection.sender.types)
        assertEquals("mouse.move", viewModel.holder.value!!.session.snapshot().repeat)
    }

    @Test
    fun sessionUsableAgainAfterBecomingVisible() = viewModelTest { connection, viewModel, _ ->
        viewModel.onHidden(changingConfigurations = false)
        advanceUntilIdle()
        viewModel.onVisible()
        advanceUntilIdle()
        viewModel.holder.value!!.session.toggleDrag()
        assertEquals("mouse.dragStart", connection.sender.types.last())
        assertEquals(0, connection.connectCalls)
    }

    @Test
    fun disconnectRetiresSessionAndReleasesInput() = viewModelTest { connection, viewModel, switchStop ->
        holdEverything(viewModel)
        connection.sender.calls.clear()
        connection.state.value = PcConnectionState.Idle(emptyList())
        advanceUntilIdle()
        assertNull(viewModel.holder.value)
        assertEquals("mouse.repeat.stop", connection.sender.types.first())
        assertNull(switchStop.armed)
        assertEquals(0, connection.cleanups.size)
    }

    @Test
    fun switchingToForwardingReleasesHeldInputAndLeavesEditMode() = viewModelTest { connection, viewModel, switchStop ->
        viewModel.toggleLayoutEditing()
        holdEverything(viewModel)
        connection.sender.calls.clear()

        viewModel.selectSurface(PcRemoteSurface.Forwarding)
        advanceUntilIdle()

        assertEquals(
            listOf("mouse.repeat.stop", "mouse.dragEnd", "keyboard.modifierUp", "keyboard.textStream.close"),
            connection.sender.types
        )
        assertNull(switchStop.armed)
        assertFalse(viewModel.editingLayout.value)
        assertEquals(PcRemoteSurface.Forwarding, viewModel.preferences.surface.value)
    }

    @Test
    fun switchingBetweenLayoutSurfacesKeepsHeldInput() = viewModelTest { connection, viewModel, _ ->
        viewModel.toggleLayoutEditing()
        holdEverything(viewModel)
        connection.sender.calls.clear()

        viewModel.selectSurface(PcRemoteSurface.Window)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), connection.sender.types)
        assertTrue(viewModel.editingLayout.value)
    }
}
