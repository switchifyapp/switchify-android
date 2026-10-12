package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcRemoteNameStore
import com.enaboapps.switchify.pc.connection.PcRemoteNameSync
import com.enaboapps.switchify.pc.control.InMemoryPcPreferenceStorage
import com.enaboapps.switchify.pc.forwarding.PcForwardingPreferenceStore
import com.enaboapps.switchify.pc.forwarding.PcForwardingPreferences
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.pc.remote.PcTypingMode
import com.enaboapps.switchify.pc.remote.PersistedPcRemotePreferences
import com.enaboapps.switchify.pc.storage.PcSavedPc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcControlSettingsViewModelTest {
    private val office = PcSavedPc("pc-1", "Office", PcPlatform.Windows, "ble-1", 2)
    private val studio = PcSavedPc("pc-2", "Studio", PcPlatform.MacOs, "ble-2", 1)

    private class FakeForwardingPreferences : PcForwardingPreferenceStore {
        var stored = 5_000L

        override fun holdToStopMs() = PcForwardingPreferences.normalizeHoldToStop(stored)

        override fun setHoldToStopMs(value: Long) {
            stored = PcForwardingPreferences.normalizeHoldToStop(value)
        }

        override fun rememberedProfileId(desktopId: String): String? = null

        override fun rememberProfileId(desktopId: String, profileId: String) = Unit
    }

    private class Fixture(saved: List<PcSavedPc>) {
        val remoteStorage = InMemoryPcPreferenceStorage()
        val controlStorage = InMemoryPcPreferenceStorage()
        val connection = FakePcControlConnection(saved)
        val forwarding = FakeForwardingPreferences()
        val names = PcRemoteNameStore(controlStorage, "Pixel 9")
        val viewModel = PcControlSettingsViewModel(connection, PersistedPcRemotePreferences(remoteStorage), names, forwarding)
    }

    private fun viewModelTest(saved: List<PcSavedPc> = listOf(office, studio), block: suspend TestScope.(Fixture) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block(Fixture(saved))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun openingSurfaceAndTypingModeArePersisted() = viewModelTest { f ->
        f.viewModel.setSurface(PcRemoteSurface.Window)
        f.viewModel.setTypingMode(PcTypingMode.Draft)
        val reloaded = PersistedPcRemotePreferences(f.remoteStorage)
        assertEquals(PcRemoteSurface.Window, reloaded.surface.value)
        assertEquals(PcTypingMode.Draft, reloaded.typingMode.value)
    }

    @Test
    fun defaultPcIsLoadedAndSaved() = viewModelTest { f ->
        f.connection.defaultId = "pc-2"
        advanceUntilIdle()
        assertEquals(listOf(office, studio), f.viewModel.saved.value)
        assertEquals("pc-2", f.viewModel.defaultDesktopId.value)

        f.viewModel.setDefault("pc-1")
        advanceUntilIdle()
        assertEquals("pc-1", f.connection.defaultId)
        assertEquals("pc-1", f.viewModel.defaultDesktopId.value)

        f.viewModel.setDefault(null)
        advanceUntilIdle()
        assertNull(f.connection.defaultId)
        assertNull(f.viewModel.defaultDesktopId.value)
    }

    @Test
    fun savedPcsRefreshWhenTheConnectionChanges() = viewModelTest(saved = emptyList()) { f ->
        advanceUntilIdle()
        assertEquals(emptyList<PcSavedPc>(), f.viewModel.saved.value)
        f.connection.saved = listOf(office)
        f.connection.state.value = PcConnectionState.Scanning(emptyList(), emptyList())
        advanceUntilIdle()
        assertEquals(listOf(office), f.viewModel.saved.value)
    }

    @Test
    fun holdToStopUsesTheForwardingPreference() = viewModelTest { f ->
        f.viewModel.setHoldToStopMs(8_000L)
        assertEquals(8_000L, f.forwarding.stored)
        assertEquals(8_000L, f.viewModel.holdToStopMs.value)
        f.viewModel.setHoldToStopMs(1_234L)
        assertEquals(5_000L, f.viewModel.holdToStopMs.value)
    }

    @Test
    fun remoteNameIsSavedAndSyncedToTheConnectedPc() = viewModelTest { f ->
        assertNull(f.viewModel.remoteName.value)
        assertEquals("Pixel 9", f.viewModel.automaticRemoteName)

        f.connection.sync = PcRemoteNameSync.Synced
        f.viewModel.saveRemoteName("Kitchen tablet")
        advanceUntilIdle()
        assertEquals("Kitchen tablet", f.viewModel.remoteName.value)
        assertEquals("Kitchen tablet", PcRemoteNameStore(f.controlStorage, "Pixel 9").resolvedName())
        assertEquals(PcRemoteNameStatus.Synced, f.viewModel.remoteNameStatus.value)
        assertFalse(f.viewModel.remoteNameSaving.value)

        f.connection.sync = PcRemoteNameSync.Failed
        f.viewModel.saveRemoteName(null)
        advanceUntilIdle()
        assertNull(f.viewModel.remoteName.value)
        assertEquals("Pixel 9", f.names.resolvedName())
        assertEquals(PcRemoteNameStatus.SyncFailed, f.viewModel.remoteNameStatus.value)
        assertEquals(2, f.connection.syncCalls)
    }

    @Test
    fun anInvalidRemoteNameIsNotSavedOrSynced() = viewModelTest { f ->
        f.viewModel.saveRemoteName("bad\nname")
        advanceUntilIdle()
        assertEquals(PcRemoteNameStatus.SaveFailed, f.viewModel.remoteNameStatus.value)
        assertNull(f.viewModel.remoteName.value)
        assertEquals(0, f.connection.syncCalls)
        assertFalse(f.viewModel.remoteNameSaving.value)
    }

    @Test
    fun refreshPicksUpChangesMadeOnOtherTabs() = viewModelTest { f ->
        advanceUntilIdle()
        f.forwarding.stored = 3_000L
        f.connection.defaultId = "pc-2"
        f.connection.saved = listOf(studio)
        f.controlStorage.values[PcRemoteNameStore.KEY] = "Desk phone"

        f.viewModel.refresh()
        advanceUntilIdle()

        assertEquals(3_000L, f.viewModel.holdToStopMs.value)
        assertEquals("pc-2", f.viewModel.defaultDesktopId.value)
        assertEquals(listOf(studio), f.viewModel.saved.value)
        assertEquals("Desk phone", f.viewModel.remoteName.value)
    }
}
