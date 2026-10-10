package com.enaboapps.switchify.screens.pc

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcList.toDesktop
import com.enaboapps.switchify.pc.connection.PcPermissionRequester
import com.enaboapps.switchify.pc.connection.PcProfileStatus
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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        var granted = true
        val permissions = PcPermissionRequester { granted }
        val viewModel = PcControlViewModel(connection, setup, permissions, preferences, cleanupScope)
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
    fun disconnectsInTheBackgroundAndReconnectsOnReturningToTheForeground() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        f.connection.state.value = PcConnectionState.Connected(office.toDesktop(), null, PcProfileStatus.Unavailable)
        f.viewModel.onStop(changingConfigurations = false)
        advanceUntilIdle()
        assertEquals(1, f.connection.disconnectCalls)
        assertEquals(PcConnectionState.Idle(listOf(office)), f.connection.state.value)
        assertEquals(1, f.connection.connectCalls)

        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(2, f.connection.connectCalls)

        f.viewModel.onStop(changingConfigurations = true)
        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(1, f.connection.disconnectCalls)
        assertEquals(2, f.connection.connectCalls)
    }

    @Test
    fun rotationKeepsTheConnectionButBackgroundAndLeavingDisconnect() = viewModelTest(saved = listOf(office)) { f ->
        f.connection.state.value = PcConnectionState.Connected(office.toDesktop(), null, PcProfileStatus.Unavailable)
        f.viewModel.onStart()
        f.viewModel.onStop(changingConfigurations = true)
        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(0, f.connection.disconnectCalls)

        f.viewModel.onStop(changingConfigurations = false)
        advanceUntilIdle()
        assertEquals(1, f.connection.disconnectCalls)

        f.viewModel.clearForTest()
        advanceUntilIdle()
        assertEquals(2, f.connection.disconnectCalls)
    }

    @Test
    fun backgroundDisconnectKeepsTheSelectedSurfaceAndTab() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.chooseOpeningSurface(PcRemoteSurface.Forwarding)
        f.viewModel.onStart()
        advanceUntilIdle()
        f.viewModel.selectTab(PcControlTab.Settings)
        f.connection.state.value = PcConnectionState.Connected(office.toDesktop(), null, PcProfileStatus.Unavailable)
        f.viewModel.onStop(changingConfigurations = false)
        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(PcRemoteSurface.Forwarding, f.viewModel.surface.value)
        assertEquals(PcControlTab.Settings, f.viewModel.tab.value)
        assertEquals(2, f.connection.connectCalls)
    }

    @Test
    fun returningFromSettingsRetriesTheChosenPcOnceInsteadOfAutoConnecting() = viewModelTest(saved = listOf(office)) { f ->
        val studio = office.copy(desktopId = "pc-2", displayName = "Studio")
        f.viewModel.onStart()
        advanceUntilIdle()
        f.connection.state.value = PcConnectionState.PermissionDenied(listOf(office, studio), retry = studio)
        f.granted = false
        f.viewModel.onStop(changingConfigurations = false)

        f.granted = true
        f.viewModel.onStart()
        f.viewModel.onResume()
        advanceUntilIdle()

        assertEquals(1, f.connection.connectCalls)
        assertEquals(listOf(studio), f.connection.connectedSaved)
        assertEquals(0, f.connection.disconnectCalls)
    }

    @Test
    fun returningWithLocationStillOffDoesNothing() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        f.connection.state.value = PcConnectionState.LocationOff(listOf(office), retry = office)
        f.connection.locationOff = true
        f.viewModel.onStop(changingConfigurations = false)
        f.viewModel.onStart()
        f.viewModel.onResume()
        advanceUntilIdle()
        assertEquals(1, f.connection.connectCalls)
        assertEquals(emptyList<PcSavedPc>(), f.connection.connectedSaved)

        f.connection.locationOff = false
        f.viewModel.onStop(changingConfigurations = false)
        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(listOf(office), f.connection.connectedSaved)
        assertEquals(0, f.connection.disconnectCalls)
    }

    @Test
    fun returningToPcsAfterABlockedSearchWithASavedPcStartsOneScanAndNoConnect() = viewModelTest(saved = listOf(office)) { f ->
        advanceUntilIdle()
        f.viewModel.selectTab(PcControlTab.Pcs)
        f.viewModel.onStart()
        advanceUntilIdle()
        f.granted = false
        f.connection.state.value = PcConnectionState.PermissionDenied(listOf(office))
        f.viewModel.onStop(changingConfigurations = false)

        f.granted = true
        f.viewModel.onStart()
        f.viewModel.onResume()
        advanceUntilIdle()

        assertEquals(1, f.connection.connectCalls)
        assertEquals(1, f.connection.scanCalls)
        assertEquals(emptyList<PcSavedPc>(), f.connection.connectedSaved)
        assertEquals(0, f.connection.disconnectCalls)
    }

    @Test
    fun returningToTheRemoteAfterABlockedSearchConnectsThePreferredPcInsteadOfScanning() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        assertEquals(PcControlTab.Remote, f.viewModel.tab.value)
        f.granted = false
        f.connection.state.value = PcConnectionState.PermissionDenied(listOf(office))
        f.viewModel.onStop(changingConfigurations = false)

        f.granted = true
        f.viewModel.onStart()
        f.viewModel.onResume()
        advanceUntilIdle()

        assertEquals(2, f.connection.connectCalls)
        assertEquals(0, f.connection.scanCalls)
        assertEquals(0, f.connection.disconnectCalls)
    }

    @Test
    fun thePermissionDialogResumingDoesNotStartASecondConnect() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        f.granted = false
        f.connection.state.value = PcConnectionState.PermissionDenied(listOf(office), retry = office)

        f.granted = true
        f.viewModel.onPermissionResult(true)
        f.viewModel.onResume()
        f.viewModel.onStart()
        advanceUntilIdle()

        assertEquals(1, f.connection.connectCalls)
        assertEquals(emptyList<PcSavedPc>(), f.connection.connectedSaved)
        assertEquals(0, f.connection.scanCalls)
    }

    @Test
    fun followsTheAppForegroundUntilCleared() = viewModelTest(saved = listOf(office)) { f ->
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle = registry
        }
        owner.registry.currentState = Lifecycle.State.STARTED
        f.viewModel.observeForeground(owner.lifecycle)
        f.viewModel.observeForeground(owner.lifecycle)
        advanceUntilIdle()
        assertEquals(1, f.connection.connectCalls)

        f.connection.state.value = PcConnectionState.Connected(office.toDesktop(), null, PcProfileStatus.Unavailable)
        owner.registry.currentState = Lifecycle.State.CREATED
        advanceUntilIdle()
        assertEquals(1, f.connection.disconnectCalls)

        owner.registry.currentState = Lifecycle.State.STARTED
        advanceUntilIdle()
        assertEquals(2, f.connection.connectCalls)

        f.viewModel.clearForTest()
        advanceUntilIdle()
        assertEquals(0, owner.registry.observerCount)
    }

    @Test
    fun aPermissionPromptThatStopsTheActivityDoesNotDisconnect() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        f.granted = false
        val request = async { f.permissions.request() }
        advanceUntilIdle()
        assertTrue(f.viewModel.permissionRequested.value)
        f.viewModel.onStop(changingConfigurations = false)
        advanceUntilIdle()
        assertEquals(0, f.connection.disconnectCalls)

        f.granted = true
        f.viewModel.onPermissionResult(true)
        assertTrue(request.await())
    }

    @Test
    fun goingToTheBackgroundEndsAnyScanButRotationDoesNot() = viewModelTest { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        f.connection.state.value = PcConnectionState.Scanning(emptyList(), emptyList())
        f.viewModel.onStop(changingConfigurations = true)
        advanceUntilIdle()
        assertEquals(0, f.connection.disconnectCalls)
        f.viewModel.onStop(changingConfigurations = false)
        advanceUntilIdle()
        assertEquals(1, f.connection.disconnectCalls)
        assertEquals(PcConnectionState.Idle(emptyList()), f.connection.state.value)
    }

    @Test
    fun aBlockedSearchStillDeniedOnReturnDoesNothing() = viewModelTest(saved = listOf(office)) { f ->
        f.viewModel.onStart()
        advanceUntilIdle()
        f.granted = false
        f.connection.state.value = PcConnectionState.LocationOff(listOf(office))
        f.connection.locationOff = true
        f.viewModel.onStop(changingConfigurations = false)
        f.viewModel.onStart()
        f.viewModel.onResume()
        advanceUntilIdle()
        assertEquals(1, f.connection.connectCalls)
        assertEquals(0, f.connection.scanCalls)
        assertEquals(0, f.connection.disconnectCalls)
    }

    @Test
    fun backReturnsToTheStartTabBeforeLeaving() = viewModelTest(saved = listOf(office)) { f ->
        advanceUntilIdle()
        assertEquals(PcControlTab.Remote, f.viewModel.startTab.value)
        assertFalse(f.viewModel.onBack())

        f.viewModel.selectTab(PcControlTab.Settings)
        assertTrue(f.viewModel.onBack())
        assertEquals(PcControlTab.Remote, f.viewModel.tab.value)
        assertFalse(f.viewModel.onBack())

        f.viewModel.selectTab(PcControlTab.Pcs)
        assertTrue(f.viewModel.onBack())
        assertEquals(PcControlTab.Remote, f.viewModel.tab.value)
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
