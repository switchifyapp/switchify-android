package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.techniques.AccessTechnique
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppScanTechniqueOverrideCoordinatorTest {
    @Test
    fun emptyAndInvalidRulesDoNotOverrideAnyApp() {
        assertTrue(AppScanTechniqueSettings.decode("{}").isEmpty())
        assertTrue(AppScanTechniqueSettings.decode("invalid").isEmpty())
        assertEquals(mapOf("app" to "radar"), AppScanTechniqueSettings.decode("{\"app\":\"radar\",\"bad\":\"menu\"}"))
    }

    @Test
    fun enteringConfiguredAppAppliesItemScanAndLeavingRestoresPointScan() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller, UnconfinedTestDispatcher(testScheduler))

        coordinator.onForegroundApplicationChanged("com.example.configured")
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())

        coordinator.onForegroundApplicationChanged("com.example.launcher")
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
    }

    @Test
    fun enteringConfiguredAppClosesExistingMenuBeforeApplyingOverrideOnce() = runTest {
        val controller = CountingScanModeController(
            initialTechnique = AccessTechnique.Technique.MENU,
            preferredTechnique = AccessTechnique.Technique.RADAR
        )
        var menuOpen = true
        var closeCalls = 0
        lateinit var coordinator: AppScanTechniqueOverrideCoordinator
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val menuActions = AppScanTechniqueOverrideMenuActions(
            dispatcher = dispatcher,
            isMenuOpen = { menuOpen },
            closeMenuHierarchy = {
                closeCalls++
                menuOpen = false
                controller.closeMenu()
                coordinator.refreshForegroundOverride()
            }
        )
        coordinator = coordinator(controller, dispatcher, menuActions)

        coordinator.onForegroundApplicationChanged("com.example.configured")

        assertEquals(1, closeCalls)
        assertEquals(1, controller.temporaryCalls)
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())

        coordinator.onForegroundApplicationChanged("com.example.launcher")
        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun repeatedConfiguredAppEventsDoNotCloseMenuOpenedAfterOverrideStarted() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        var menuOpen = false
        var closeCalls = 0
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val menuActions = AppScanTechniqueOverrideMenuActions(
            dispatcher = dispatcher,
            isMenuOpen = { menuOpen },
            closeMenuHierarchy = { closeCalls++ }
        )
        val coordinator = coordinator(controller, dispatcher, menuActions)

        coordinator.onForegroundApplicationChanged("com.example.configured")
        controller.openMenu()
        menuOpen = true
        coordinator.onForegroundApplicationChanged("com.example.configured")

        assertEquals(0, closeCalls)
        assertEquals(AccessTechnique.Technique.MENU, controller.currentTechnique())

        menuOpen = false
        controller.closeMenu()
        coordinator.refreshForegroundOverride()
        assertEquals(2, controller.temporaryCalls)
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun repeatedConfiguredAppEventsAndActivityTransitionsKeepOneSession() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.RADAR)
        val coordinator = coordinator(controller, UnconfinedTestDispatcher(testScheduler))

        coordinator.onForegroundApplicationChanged("com.example.configured")
        coordinator.onForegroundApplicationChanged("com.example.configured")

        assertEquals(1, controller.temporaryCalls)
        coordinator.onForegroundApplicationChanged("com.example.launcher")
        assertEquals(1, controller.restoreCalls)
        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun missingWindowDoesNotEndConfiguredAppSession() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller, UnconfinedTestDispatcher(testScheduler))

        coordinator.onForegroundApplicationChanged("com.example.configured")
        coordinator.onForegroundApplicationChanged(null)

        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun manualTechniqueChangeIsTemporaryForTheVisit() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller, UnconfinedTestDispatcher(testScheduler))

        coordinator.onForegroundApplicationChanged("com.example.configured")
        assertTrue(coordinator.selectForCurrentVisit(AccessTechnique.Technique.RADAR))
        coordinator.refreshForegroundOverride()
        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
        coordinator.onForegroundApplicationChanged("com.example.launcher")

        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
        coordinator.onForegroundApplicationChanged("com.example.configured")
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun clearRestoresTechniqueAndAllowsConfiguredAppToStartAnotherSession() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller, UnconfinedTestDispatcher(testScheduler))

        coordinator.onForegroundApplicationChanged("com.example.configured")
        coordinator.clear()
        coordinator.onForegroundApplicationChanged("com.example.configured")

        assertEquals(2, controller.temporaryCalls)
        assertEquals(1, controller.restoreCalls)
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun unrelatedApplicationDoesNotCloseMenuOrChangeTechnique() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.RADAR)
        var closeCalls = 0
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val menuActions = AppScanTechniqueOverrideMenuActions(
            dispatcher = dispatcher,
            isMenuOpen = { true },
            closeMenuHierarchy = { closeCalls++ }
        )
        val coordinator = coordinator(controller, dispatcher, menuActions)

        coordinator.onForegroundApplicationChanged("com.example.other")

        assertEquals(0, closeCalls)
        assertEquals(0, controller.temporaryCalls)
        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun menuCloseRefreshesConfiguredAppOverrideAndLeavingRestoresPointScan() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller, UnconfinedTestDispatcher(testScheduler))

        coordinator.onForegroundApplicationChanged("com.example.configured")
        controller.setPersistentTechnique(AccessTechnique.Technique.POINT_SCAN)
        coordinator.refreshForegroundOverride()

        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
        assertEquals(2, controller.temporaryCalls)

        coordinator.onForegroundApplicationChanged("com.example.launcher")
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
    }

    @Test
    fun refreshIsIdempotentWhileConfiguredAppOverrideIsActive() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val coordinator = coordinator(controller, UnconfinedTestDispatcher(testScheduler))

        coordinator.onForegroundApplicationChanged("com.example.configured")
        coordinator.refreshForegroundOverride()
        coordinator.refreshForegroundOverride()

        assertEquals(1, controller.temporaryCalls)
    }

    @Test
    fun newerForegroundTransitionSuppressesQueuedConfiguredAppEntry() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = coordinator(controller, dispatcher)

        launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.onForegroundApplicationChanged("com.example.configured")
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.onForegroundApplicationChanged("com.example.launcher")
        }
        runCurrent()

        assertEquals(0, controller.temporaryCalls)
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
    }

    @Test
    fun clearSuppressesQueuedConfiguredAppEntry() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = coordinator(controller, dispatcher)

        launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.onForegroundApplicationChanged("com.example.configured")
        }
        coordinator.clear()
        runCurrent()

        assertEquals(0, controller.temporaryCalls)
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
    }

    @Test
    fun foregroundTransitionRunsThroughUiDispatcher() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = coordinator(controller, dispatcher)

        launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.onForegroundApplicationChanged("com.example.configured")
        }

        assertEquals(0, controller.temporaryCalls)
        runCurrent()
        assertEquals(1, controller.temporaryCalls)
    }

    @Test
    fun menuActionsRunThroughUiDispatcherAndRecheckGeneration() = runTest {
        val calls = mutableListOf<String>()
        var current = true
        var result: Boolean? = null
        val actions = AppScanTechniqueOverrideMenuActions(
            dispatcher = StandardTestDispatcher(testScheduler),
            isMenuOpen = { true },
            closeMenuHierarchy = { calls += "close" }
        )

        launch(start = CoroutineStart.UNDISPATCHED) {
            result = actions.closeIfOpen { current }
        }
        assertTrue(calls.isEmpty())
        current = false
        runCurrent()

        assertFalse(calls.contains("close"))
        assertEquals(false, result)
    }

    @Test
    fun ruleEditsRespectManualChoiceAndRemovalRestoresDefault() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.POINT_SCAN)
        var rule: String? = AccessTechnique.Technique.ITEM_SCAN
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val coordinator = AppScanTechniqueOverrideCoordinator(controller, AppScanTechniquePolicy { rule },
            AppScanTechniqueOverrideMenuActions(dispatcher, { false }, {}), dispatcher)
        coordinator.onForegroundApplicationChanged("app")
        rule = AccessTechnique.Technique.RADAR
        coordinator.refreshForegroundOverride()
        assertEquals(rule, controller.currentTechnique())
        coordinator.selectForCurrentVisit(AccessTechnique.Technique.ITEM_SCAN)
        rule = AccessTechnique.Technique.POINT_SCAN
        coordinator.refreshForegroundOverride()
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
        rule = null
        coordinator.refreshForegroundOverride()
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
        assertFalse(controller.isTemporaryTechniqueActive())
    }

    @Test
    fun menuClosePreservesManualChoiceAndSameTechniqueRuleStillOwnsVisit() = runTest {
        val controller = CountingScanModeController(AccessTechnique.Technique.ITEM_SCAN)
        val coordinator = coordinator(controller, UnconfinedTestDispatcher(testScheduler))
        coordinator.onForegroundApplicationChanged("com.example.configured")
        coordinator.selectForCurrentVisit(AccessTechnique.Technique.RADAR)
        controller.openMenu()
        coordinator.refreshForegroundOverride()
        assertEquals(AccessTechnique.Technique.MENU, controller.currentTechnique())
        controller.closeMenu()
        coordinator.refreshForegroundOverride()
        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
        coordinator.onForegroundApplicationChanged("other")
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    private fun coordinator(
        controller: ScanModeController,
        dispatcher: CoroutineDispatcher,
        menuActions: AppScanTechniqueOverrideMenuActions = AppScanTechniqueOverrideMenuActions(
            dispatcher = dispatcher,
            isMenuOpen = { false },
            closeMenuHierarchy = {}
        )
    ) = AppScanTechniqueOverrideCoordinator(
        controller = controller,
        policy = AppScanTechniquePolicy { if (it == "com.example.configured") AccessTechnique.Technique.ITEM_SCAN else null },
        menuActions = menuActions,
        uiDispatcher = dispatcher
    )

    private class CountingScanModeController(
        initialTechnique: String,
        private var preferredTechnique: String = initialTechnique
    ) : ScanModeController {
        private var technique = initialTechnique
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

        fun openMenu() {
            technique = AccessTechnique.Technique.MENU
            temporary = false
        }

        fun closeMenu() {
            technique = preferredTechnique
            temporary = false
        }
    }
}
