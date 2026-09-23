package com.enaboapps.switchify.service.menu

import com.enaboapps.switchify.service.techniques.nodes.NodeActionBounds
import com.enaboapps.switchify.service.techniques.nodes.NodeActionIdentity
import com.enaboapps.switchify.service.techniques.nodes.NodeActionLocator
import com.enaboapps.switchify.service.techniques.nodes.NodeActionTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityActionMenuActionsTest {
    @Test
    fun allCallbacksUseInjectedUiDispatcher() = runTest {
        val events = mutableListOf<String>()
        val actions = AndroidAccessibilityActionMenuActions(
            dispatcher = StandardTestDispatcher(testScheduler),
            openMenu = { events += "open" },
            replaceMenu = { events += "replace" },
            showMain = { events += "main" },
            closeMenus = { events += "close" }
        )

        launch(start = CoroutineStart.UNDISPATCHED) {
            actions.open(target()) { true }
            actions.replace(target()) { true }
            actions.showMainWithoutActions { true }
            actions.closeAll { true }
        }
        runCurrent()

        assertEquals(listOf("open", "replace", "main", "close"), events)
    }

    @Test
    fun staleGenerationSuppressesEveryCallback() = runTest {
        val events = mutableListOf<String>()
        val actions = AndroidAccessibilityActionMenuActions(
            dispatcher = StandardTestDispatcher(testScheduler),
            openMenu = { events += "open" },
            replaceMenu = { events += "replace" },
            showMain = { events += "main" },
            closeMenus = { events += "close" }
        )

        launch(start = CoroutineStart.UNDISPATCHED) {
            actions.open(target()) { false }
            actions.replace(target()) { false }
            actions.showMainWithoutActions { false }
            actions.closeAll { false }
        }
        runCurrent()

        assertEquals(emptyList<String>(), events)
    }

    private fun target() = NodeActionTarget(
        locator = NodeActionLocator(
            identity = NodeActionIdentity(
                packageName = "app",
                windowId = 1,
                childPath = listOf(0),
                bounds = NodeActionBounds(0, 0, 10, 10),
                className = "Button",
                text = "Open",
                contentDescription = null,
                viewIdResourceName = "open",
                uniqueId = null
            ),
            selectionX = 5f,
            selectionY = 5f,
            reportedActionIds = setOf(1)
        ),
        actions = emptyList()
    )
}
