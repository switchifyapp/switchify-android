package com.enaboapps.switchify.screens.pc.remote

import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcProfileStatus
import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import com.enaboapps.switchify.pc.remote.FakeRemoteSender
import com.enaboapps.switchify.pc.remote.FakeSwitchStop
import com.enaboapps.switchify.pc.remote.InMemoryPcRemotePreferences
import com.enaboapps.switchify.pc.remote.PcRemoteConnection
import com.enaboapps.switchify.pc.remote.actions.PcActionContext
import com.enaboapps.switchify.pc.remote.actions.PcActionRuntime
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction
import com.enaboapps.switchify.pc.remote.layouts.InMemoryPcLayoutStore
import com.enaboapps.switchify.pc.remote.layouts.PcButtonLayout
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutAxis
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSelection
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSelectionKind
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutStore
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSurface
import com.enaboapps.switchify.pc.remote.layouts.PcSurfaceLayouts
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
import kotlinx.coroutines.flow.StateFlow
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

private class LayoutTestConnection : PcRemoteConnection {
    val sender = FakeRemoteSender()

    override val state = MutableStateFlow<PcConnectionState>(PcConnectionState.Idle(emptyList()))

    override suspend fun send(command: PcCommand, responseMode: PcResponseMode) = sender.send(command, responseMode)

    override fun registerCleanup(cleanup: suspend () -> Unit): PcUnsubscribe = PcUnsubscribe { }

    override suspend fun connectPreferred() = Unit

    override suspend fun listSaved(): List<PcSavedPc> = emptyList()

    override suspend fun switchSaved(pc: PcSavedPc) = Unit
}

private class FlakyLayoutStore(private val delegate: InMemoryPcLayoutStore = InMemoryPcLayoutStore()) : PcLayoutStore {
    var failures = 0
    override val layouts: StateFlow<PcSurfaceLayouts> = delegate.layouts

    override suspend fun save(surface: PcLayoutSurface, section: String, layout: PcButtonLayout?) {
        if (failures > 0) {
            failures -= 1
            throw IllegalStateException("private details")
        }
        delegate.save(surface, section, layout)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PcRemoteLayoutEditorTest {
    private val office = PcDiscoveredDesktop("desktop-1", "Office", PcPlatform.Windows, null, "ble-1", -40)
    private val home = PcDiscoveredDesktop("desktop-2", "Home", PcPlatform.Windows, null, "ble-2", -40)
    private val preferences = InMemoryPcRemotePreferences()
    private val store = FlakyLayoutStore()

    private fun layoutTest(block: suspend TestScope.(LayoutTestConnection, PcRemoteViewModel) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val sessionScope = CoroutineScope(SupervisorJob() + dispatcher)
        val connection = LayoutTestConnection()
        val viewModel = PcRemoteViewModel(connection, preferences, store, FakeSwitchStop(), sessionScope)
        try {
            connection.state.value = PcConnectionState.Connected(office, remoteProfile(), PcProfileStatus.Ready)
            advanceUntilIdle()
            block(connection, viewModel)
        } finally {
            sessionScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun controls(viewModel: PcRemoteViewModel): List<PcResolvedAction> = PcActionRuntime.resolve(
        PcActionContext(PcLayoutSurface.Mouse, viewModel.holder.value!!.session, PcPlatform.Windows, preferences)
    )

    private suspend fun TestScope.open(viewModel: PcRemoteViewModel) {
        viewModel.openLayoutEditor(PcLayoutSurface.Mouse, "clicks", controls(viewModel), 460f, 1f)
        advanceUntilIdle()
    }

    @Test
    fun opensOnlyInEditModeWhenInputIsNotHeld() = layoutTest { _, viewModel ->
        open(viewModel)
        assertNull(viewModel.layoutEditor.value)
        viewModel.toggleLayoutEditing()
        viewModel.holder.value!!.session.toggleDrag()
        advanceUntilIdle()
        assertTrue(viewModel.layoutEditingBlocked())
        open(viewModel)
        assertNull(viewModel.layoutEditor.value)
        viewModel.holder.value!!.session.toggleDrag()
        advanceUntilIdle()
        open(viewModel)
        val session = viewModel.layoutEditor.value!!
        assertEquals(
            PcButtonLayout(3, listOf("click.double", "click.right", "drag.toggle", "scroll.up", "scroll.down", null)),
            session.state.draft
        )
        assertFalse(session.state.initiallyCustomized)
    }

    @Test
    fun unsavedEditsSurviveDisconnectAndReconnect() = layoutTest { connection, viewModel ->
        viewModel.toggleLayoutEditing()
        open(viewModel)
        viewModel.editLayout { it.resize(PcLayoutAxis.Row, it.rows, true) }
        val draft = viewModel.layoutEditor.value!!.state.draft
        connection.state.value = PcConnectionState.Idle(emptyList())
        advanceUntilIdle()
        assertEquals(draft, viewModel.layoutEditor.value!!.state.draft)
        connection.state.value = PcConnectionState.Connected(office, remoteProfile(), PcProfileStatus.Ready)
        advanceUntilIdle()
        assertEquals(draft, viewModel.layoutEditor.value!!.state.draft)
        assertTrue(viewModel.editingLayout.value)
        viewModel.saveLayoutEditor()
        advanceUntilIdle()
        assertNull(viewModel.layoutEditor.value)
        assertEquals(draft, store.layouts.value[PcLayoutSurface.Mouse]?.get("clicks"))
    }

    @Test
    fun savesWithoutAConnectionAndSendsNoCommands() = layoutTest { connection, viewModel ->
        viewModel.toggleLayoutEditing()
        open(viewModel)
        connection.sender.calls.clear()
        connection.state.value = PcConnectionState.Idle(emptyList())
        advanceUntilIdle()
        viewModel.editLayout { it.select(PcLayoutSelection(PcLayoutSelectionKind.Cell, 0)).removeSelectedCell() }
        viewModel.saveLayoutEditor()
        advanceUntilIdle()
        assertEquals(null, store.layouts.value[PcLayoutSurface.Mouse]?.get("clicks")?.cells?.first())
        assertEquals(emptyList<String>(), connection.sender.types.filter { it != "keyboard.textStream.close" })
    }

    @Test
    fun switchingToAnotherPcLeavesEditMode() = layoutTest { connection, viewModel ->
        viewModel.toggleLayoutEditing()
        connection.state.value = PcConnectionState.Connected(home, remoteProfile(), PcProfileStatus.Ready)
        advanceUntilIdle()
        assertFalse(viewModel.editingLayout.value)
    }

    @Test
    fun keepsTheDraftAfterASanitizedSaveFailureAndRetries() = layoutTest { _, viewModel ->
        viewModel.toggleLayoutEditing()
        open(viewModel)
        viewModel.editLayout { it.resize(PcLayoutAxis.Row, it.rows, true) }
        store.failures = 1
        viewModel.saveLayoutEditor()
        advanceUntilIdle()
        val failed = viewModel.layoutEditor.value!!
        assertTrue(failed.failed)
        assertFalse(failed.saving)
        assertEquals(1, failed.failureCount)
        assertTrue(failed.state.dirty)
        viewModel.saveLayoutEditor()
        advanceUntilIdle()
        assertNull(viewModel.layoutEditor.value)
        assertEquals(failed.state.draft, store.layouts.value[PcLayoutSurface.Mouse]?.get("clicks"))
    }

    @Test
    fun unchangedSaveLeavesAdaptiveDefaults() = layoutTest { _, viewModel ->
        viewModel.toggleLayoutEditing()
        open(viewModel)
        viewModel.saveLayoutEditor()
        advanceUntilIdle()
        assertNull(viewModel.layoutEditor.value)
        assertTrue(store.layouts.value.isEmpty())
    }

    @Test
    fun backClosesThePickerThenConfirmsDiscardingEdits() = layoutTest { _, viewModel ->
        viewModel.toggleLayoutEditing()
        open(viewModel)
        viewModel.editLayout { it.resize(PcLayoutAxis.Row, it.rows, true) }
        viewModel.editLayout { it.select(PcLayoutSelection(PcLayoutSelectionKind.Cell, 6)) }
        assertNotNull(viewModel.layoutEditor.value!!.state.pickerCell)
        viewModel.dismissLayoutEditor()
        assertNull(viewModel.layoutEditor.value!!.state.pickerCell)
        viewModel.dismissLayoutEditor()
        assertEquals(PcLayoutConfirmation.Discard, viewModel.layoutEditor.value!!.confirmation)
        viewModel.requestLayoutConfirmation(null)
        assertNotNull(viewModel.layoutEditor.value)
        viewModel.dismissLayoutEditor()
        viewModel.confirmLayout()
        assertNull(viewModel.layoutEditor.value)
        assertTrue(store.layouts.value.isEmpty())
    }

    @Test
    fun confirmsResetAndRemovingAnOccupiedRow() = layoutTest { _, viewModel ->
        viewModel.toggleLayoutEditing()
        open(viewModel)
        viewModel.requestLayoutConfirmation(PcLayoutConfirmation.RemoveTrack(PcLayoutAxis.Row, 0))
        viewModel.confirmLayout()
        assertEquals(listOf("scroll.up", "scroll.down", null), viewModel.layoutEditor.value!!.state.draft.cells)
        viewModel.requestLayoutConfirmation(PcLayoutConfirmation.Reset)
        viewModel.confirmLayout()
        val reset = viewModel.layoutEditor.value!!
        assertTrue(reset.state.reset)
        assertNull(reset.confirmation)
    }

    @Test
    fun refreshesControlsOnlyForTheOpenSection() = layoutTest { _, viewModel ->
        viewModel.toggleLayoutEditing()
        open(viewModel)
        viewModel.refreshLayoutEditorControls(PcLayoutSurface.Mouse, "speed", emptyList())
        assertTrue(viewModel.layoutEditor.value!!.controls.isNotEmpty())
        viewModel.refreshLayoutEditorControls(PcLayoutSurface.Mouse, "clicks", emptyList())
        assertTrue(viewModel.layoutEditor.value!!.controls.isEmpty())
    }
}
