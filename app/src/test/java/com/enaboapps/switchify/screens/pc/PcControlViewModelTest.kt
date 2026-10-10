package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.connection.PcPermissionRequester
import com.enaboapps.switchify.pc.control.InMemoryPcPreferenceStorage
import com.enaboapps.switchify.pc.control.PcControlSetup
import com.enaboapps.switchify.pc.control.PcControlSetupPhase
import com.enaboapps.switchify.pc.control.PcControlTab
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.remote.InMemoryPcRemotePreferences
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.pc.storage.PcSavedPc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcControlViewModelTest {
    private val office = PcSavedPc("pc-1", "Office", PcPlatform.Windows, "ble-1", 1)

    private class Fixture(test: TestScope, saved: List<PcSavedPc>, setupComplete: Boolean) {
        val storage = InMemoryPcPreferenceStorage()
        val setup = PcControlSetup(storage).also { if (setupComplete) it.complete() }
        val connection = FakePcControlConnection(saved)
        val preferences = InMemoryPcRemotePreferences()
        val cleanupScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler))
        val viewModel = PcControlViewModel(connection, setup, PcPermissionRequester { true }, preferences, cleanupScope)
    }

    private fun viewModelTest(
        saved: List<PcSavedPc> = emptyList(),
        setupComplete: Boolean = true,
        block: suspend TestScope.(Fixture) -> Unit
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(this, saved, setupComplete)
        try {
            block(fixture)
        } finally {
            fixture.cleanupScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun opensTheRemoteForASavedPcAndConnectsToIt() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(PcControlTab.Remote, f.viewModel.tab.value)
        assertEquals(1, f.connection.connectCalls)
    }

    @Test
    fun opensPcsWhenNothingIsSaved() = viewModelTest { f ->
        advanceUntilIdle()
        assertEquals(PcControlTab.Pcs, f.viewModel.tab.value)
    }

    @Test
    fun reconnectsOnReturningToTheForegroundWithoutDisconnectingInTheBackground() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        f.viewModel.onStop(changingConfigurations = false)
        advanceUntilIdle()
        assertEquals(0, f.connection.cancelCalls)
        assertEquals(1, f.connection.connectCalls)

        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(2, f.connection.connectCalls)

        f.viewModel.onStop(changingConfigurations = true)
        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(2, f.connection.connectCalls)
    }

    @Test
    fun leavingPcControlOnlyCancelsAPendingPreferredConnection() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        f.viewModel.clearForTest()
        advanceUntilIdle()
        assertEquals(1, f.connection.cancelCalls)
    }

    @Test
    fun setupBlocksAutoConnectUntilFinishedAndAllowSearches() = viewModelTest(saved = listOf(office), setupComplete = false) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(0, f.connection.connectCalls)
        assertNull(f.viewModel.tab.value)
        assertEquals(PcControlSetupPhase.Welcome, f.viewModel.setupPhase.value)

        f.viewModel.continueSetup()
        f.viewModel.chooseOpeningSurface(PcRemoteSurface.Typing)
        f.viewModel.continueSetup()
        assertEquals(PcControlSetupPhase.Bluetooth, f.viewModel.setupPhase.value)
        f.viewModel.finishSetup(searchForPcs = true)
        f.viewModel.finishSetup(searchForPcs = true)
        advanceUntilIdle()

        assertEquals(PcControlSetupPhase.Complete, f.viewModel.setupPhase.value)
        assertEquals(PcControlTab.Pcs, f.viewModel.tab.value)
        assertEquals(PcRemoteSurface.Typing, f.preferences.surface.value)
        assertEquals(1, f.connection.scanCalls)
        assertEquals(0, f.connection.connectCalls)
        assertEquals(true, PcControlSetup(f.storage).isComplete)
    }

    @Test
    fun notNowFinishesSetupWithoutSearching() = viewModelTest(setupComplete = false) { f ->
        f.viewModel.finishSetup(searchForPcs = false)
        advanceUntilIdle()
        assertEquals(PcControlSetupPhase.Complete, f.viewModel.setupPhase.value)
        assertEquals(0, f.connection.scanCalls)
    }
}
