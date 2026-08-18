package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.remotebridge.SwitchifyRemoteLauncher
import com.enaboapps.switchify.service.techniques.AccessTechnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppScanTechniqueOverrideCoordinatorTest {
    @Test
    fun defaultPolicyMapsOnlySwitchifyRemoteToItemScan() {
        assertEquals(
            AccessTechnique.Technique.ITEM_SCAN,
            DefaultAppScanTechniquePolicy.techniqueFor(SwitchifyRemoteLauncher.REMOTE_PACKAGE)
        )
        assertNull(DefaultAppScanTechniquePolicy.techniqueFor("com.example.other"))
    }

    @Test
    fun enteringRemoteAppliesItemScanAndLeavingRestoresPointScan() {
        val controller = FakeScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller)

        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())

        coordinator.onForegroundApplicationChanged("com.example.launcher")
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
    }

    @Test
    fun repeatedRemoteEventsAndActivityTransitionsKeepOneSession() {
        val controller = CountingScanModeController(AccessTechnique.Technique.RADAR)
        val coordinator = coordinator(controller)

        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)
        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)

        assertEquals(1, controller.temporaryCalls)
        coordinator.onForegroundApplicationChanged("com.example.launcher")
        assertEquals(1, controller.restoreCalls)
        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun missingWindowDoesNotEndRemoteSession() {
        val controller = FakeScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller)

        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)
        coordinator.onForegroundApplicationChanged(null)

        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun manualTechniqueChangeIsPreservedOnExit() {
        val controller = FakeScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller)

        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)
        controller.setPersistentTechnique(AccessTechnique.Technique.RADAR)
        coordinator.onForegroundApplicationChanged("com.example.launcher")

        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun clearRestoresTechniqueAndAllowsRemoteToStartAnotherSession() {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller)

        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)
        coordinator.clear()
        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)

        assertEquals(2, controller.temporaryCalls)
        assertEquals(1, controller.restoreCalls)
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun unrelatedApplicationDoesNotChangeTechnique() {
        val controller = CountingScanModeController(AccessTechnique.Technique.RADAR)
        val coordinator = coordinator(controller)

        coordinator.onForegroundApplicationChanged("com.example.other")

        assertEquals(0, controller.temporaryCalls)
        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun menuCloseRefreshesRemoteOverrideAndLeavingRestoresPointScan() {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller)

        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)
        controller.setPersistentTechnique(AccessTechnique.Technique.POINT_SCAN)
        coordinator.refreshForegroundOverride()

        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
        assertEquals(2, controller.temporaryCalls)

        coordinator.onForegroundApplicationChanged("com.example.launcher")
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
    }

    @Test
    fun manualTechniqueChangeAfterRefreshIsPreservedOnExit() {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller)

        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)
        controller.setPersistentTechnique(AccessTechnique.Technique.POINT_SCAN)
        coordinator.refreshForegroundOverride()
        controller.setPersistentTechnique(AccessTechnique.Technique.RADAR)
        coordinator.onForegroundApplicationChanged("com.example.launcher")

        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun refreshIsIdempotentWhileRemoteOverrideIsActive() {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller)

        coordinator.onForegroundApplicationChanged(SwitchifyRemoteLauncher.REMOTE_PACKAGE)
        coordinator.refreshForegroundOverride()
        coordinator.refreshForegroundOverride()

        assertEquals(1, controller.temporaryCalls)
    }

    @Test
    fun refreshDoesNothingWithoutConfiguredForegroundApplication() {
        val controller = CountingScanModeController(AccessTechnique.Technique.RADAR)
        val coordinator = coordinator(controller)

        coordinator.refreshForegroundOverride()
        coordinator.onForegroundApplicationChanged("com.example.other")
        coordinator.refreshForegroundOverride()

        assertEquals(0, controller.temporaryCalls)
        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    private fun coordinator(controller: ScanModeController) =
        AppScanTechniqueOverrideCoordinator(controller, DefaultAppScanTechniquePolicy)

    private class CountingScanModeController(initialTechnique: String) : ScanModeController {
        private var technique = initialTechnique
        private var preferredTechnique = initialTechnique
        private var temporary = false
        var temporaryCalls = 0
        var restoreCalls = 0

        override fun currentTechnique(): String = technique
        override fun preferredTechnique(): String = preferredTechnique
        override fun isTemporaryTechniqueActive(): Boolean = temporary

        override fun setTemporaryTechnique(technique: String) {
            temporaryCalls++
            this.technique = technique
            temporary = true
        }

        override fun restoreTemporaryTechnique(technique: String) {
            if (!temporary) return
            restoreCalls++
            this.technique = technique
            preferredTechnique = technique
            temporary = false
        }

        fun setPersistentTechnique(technique: String) {
            this.technique = technique
            preferredTechnique = technique
            temporary = false
        }
    }
}
