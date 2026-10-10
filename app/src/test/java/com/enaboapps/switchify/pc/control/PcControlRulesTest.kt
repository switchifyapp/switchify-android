package com.enaboapps.switchify.pc.control

import com.enaboapps.switchify.pc.connection.PcConnectionFailure
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.screens.pc.remote.PcSurfaceSelectorLayout
import com.enaboapps.switchify.pc.storage.PcSavedPc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcControlRulesTest {
    private val office = PcSavedPc("pc-1", "Office", PcPlatform.Windows, "ble-1", 1)

    @Test
    fun opensPcsUntilAPcIsSavedAndThenTheRemote() {
        assertEquals(PcControlTab.Pcs, PcControlOpening.initialTab(emptyList()))
        assertEquals(PcControlTab.Remote, PcControlOpening.initialTab(listOf(office)))
        assertEquals(PcControlTab.Pcs, PcControlOpening.tabAfterSetup())
    }

    @Test
    fun autoConnectsOnOpenAndWhenReturningToTheForeground() {
        val policy = PcAutoConnectPolicy()
        assertTrue(policy.onStart(setupComplete = true))
        assertFalse(policy.onStart(setupComplete = true))
        policy.onStop(changingConfigurations = false)
        assertTrue(policy.onStart(setupComplete = true))
    }

    @Test
    fun rotationDoesNotReconnect() {
        val policy = PcAutoConnectPolicy()
        assertTrue(policy.onStart(setupComplete = true))
        policy.onStop(changingConfigurations = true)
        assertFalse(policy.onStart(setupComplete = true))
    }

    @Test
    fun neverAutoConnectsBeforeSetupIsComplete() {
        val policy = PcAutoConnectPolicy()
        assertFalse(policy.onStart(setupComplete = false))
        policy.onStop(changingConfigurations = false)
        assertFalse(policy.onStart(setupComplete = false))
        policy.onStop(changingConfigurations = false)
        assertTrue(policy.onStart(setupComplete = true))
    }

    @Test
    fun setupWalksForwardAndBackAndPersistsCompletion() {
        val storage = InMemoryPcPreferenceStorage()
        val setup = PcControlSetup(storage)
        assertEquals(PcControlSetupPhase.Welcome, setup.phase.value)
        assertFalse(setup.back())
        setup.next()
        assertEquals(PcControlSetupPhase.OpeningSurface, setup.phase.value)
        setup.next()
        assertEquals(PcControlSetupPhase.Bluetooth, setup.phase.value)
        setup.next()
        assertEquals(PcControlSetupPhase.Bluetooth, setup.phase.value)
        assertTrue(setup.back())
        assertEquals(PcControlSetupPhase.OpeningSurface, setup.phase.value)
        assertFalse(PcControlSetup(storage).isComplete)

        setup.complete()
        assertTrue(setup.isComplete)
        assertFalse(setup.back())
        assertEquals(PcControlSetupPhase.Complete, PcControlSetup(storage).phase.value)
        assertEquals(3, PcControlSetupPhase.STEP_COUNT)
        assertEquals(3, PcControlSetupPhase.Bluetooth.stepNumber)
    }

    @Test
    fun resumeRecoveryRetriesTheChosenPcOnlyOnceTheBlockerIsResolved() {
        val denied = PcConnectionState.PermissionDenied(listOf(office), retry = office)
        assertNull(PcResumeRecovery.after(denied, permissionGranted = false, locationOff = false))
        assertEquals(PcResumeRecovery.RetrySaved(office), PcResumeRecovery.after(denied, permissionGranted = true, locationOff = false))
        assertEquals(PcResumeRecovery.Rescan, PcResumeRecovery.after(denied.copy(retry = null), permissionGranted = true, locationOff = false))

        val locationOff = PcConnectionState.LocationOff(listOf(office), retry = office)
        assertNull(PcResumeRecovery.after(locationOff, permissionGranted = true, locationOff = true))
        assertEquals(PcResumeRecovery.RetrySaved(office), PcResumeRecovery.after(locationOff, permissionGranted = true, locationOff = false))

        assertNull(PcResumeRecovery.after(PcConnectionState.Idle(listOf(office)), permissionGranted = true, locationOff = false))
        assertEquals(office, PcResumeRecovery.pendingRetry(denied))
        assertNull(PcResumeRecovery.pendingRetry(PcConnectionState.Failed(PcConnectionFailure.CouldNotConnect, listOf(office))))
    }

    @Test
    fun surfaceSelectorUsesOneRowWhenEveryLabelFitsAndTwoRowsOtherwise() {
        assertEquals(4, PcSurfaceSelectorLayout.columns(4, availableWidth = 320f, widestLabel = 66f, labelPadding = 12f))
        assertEquals(2, PcSurfaceSelectorLayout.columns(4, availableWidth = 320f, widestLabel = 70f, labelPadding = 12f))
        assertEquals(1, PcSurfaceSelectorLayout.columns(1, availableWidth = 10f, widestLabel = 70f, labelPadding = 12f))
    }
}
