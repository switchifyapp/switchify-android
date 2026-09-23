package com.enaboapps.switchify.service.techniques.nodes

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityActionCoordinatorTest {
    @Test
    fun cancellingMenuCancelsResolverWork() = runTest {
        var stopped = false
        val menu = FakeMenuActions()
        val coordinator = AccessibilityActionCoordinator(
            this,
            NodeActionResolver { _, _ ->
                try {
                    awaitCancellation()
                } finally {
                    stopped = true
                }
            },
            menu
        )
        coordinator.open(locator())
        runCurrent()
        coordinator.cancel()
        runCurrent()
        assertTrue(stopped)
        assertTrue(menu.events.isEmpty())
        assertFalse(coordinator.isResolving())
    }

    @Test
    fun traversalLimitRemovesActionsWithoutExecuting() = runTest {
        val menu = FakeMenuActions()
        val coordinator = AccessibilityActionCoordinator(
            this, NodeActionResolver { _, _ -> throw NodeTraversalLimitException() }, menu
        )
        coordinator.open(locator())
        runCurrent()
        assertEquals(listOf("main"), menu.events)
        assertFalse(coordinator.isResolving())
    }

    @Test
    fun opensFreshActionsWithoutExecuting() = runTest {
        val menu = FakeMenuActions()
        val coordinator = coordinator(
            resolution(actions = listOf(action(1))),
            menu
        )

        coordinator.open(locator())
        runCurrent()

        assertEquals(listOf("open:1"), menu.events)
        assertFalse(coordinator.isResolving())
    }

    @Test
    fun removedActionRefreshesMenuWithoutExecuting() = runTest {
        var performed = false
        val menu = FakeMenuActions()
        val coordinator = coordinator(
            resolution(actions = listOf(action(2))) { performed = true; true },
            menu
        )

        coordinator.select(target(actions = listOf(action(1), action(2))), 1)
        runCurrent()

        assertEquals(listOf("replace:2"), menu.events)
        assertFalse(performed)
    }

    @Test
    fun failedActionIsExcludedAndRemainingActionsStayOpen() = runTest {
        val menu = FakeMenuActions()
        val coordinator = coordinator(
            resolution(actions = listOf(action(1), action(2))) { false },
            menu
        )

        coordinator.select(target(actions = listOf(action(1), action(2))), 1)
        runCurrent()

        assertEquals(listOf("replace:2"), menu.events)
        assertEquals(setOf(1), menu.lastTarget?.excludedActionIds)
    }

    @Test
    fun noRemainingActionsRebuildsMainMenu() = runTest {
        val menu = FakeMenuActions()
        val coordinator = coordinator(
            resolution(actions = listOf(action(1))) { false },
            menu
        )

        coordinator.select(target(actions = listOf(action(1))), 1)
        runCurrent()

        assertEquals(listOf("main"), menu.events)
    }

    @Test
    fun missingSourceClosesHierarchy() = runTest {
        val menu = FakeMenuActions()
        val coordinator = coordinator(NodeActionResolution.SourceMissing, menu)

        coordinator.select(target(actions = listOf(action(1))), 1)
        runCurrent()

        assertEquals(listOf("close"), menu.events)
    }

    @Test
    fun successfulActionExecutesOnceAndCloses() = runTest {
        var executions = 0
        val menu = FakeMenuActions()
        val coordinator = coordinator(
            resolution(actions = listOf(action(1))) { executions += 1; true },
            menu
        )

        coordinator.select(target(actions = listOf(action(1))), 1)
        coordinator.select(target(actions = listOf(action(1))), 1)
        runCurrent()

        assertEquals(1, executions)
        assertEquals(listOf("close"), menu.events)
    }

    @Test
    fun cancellationBeforeResolutionSuppressesStaleMenu() = runTest {
        val menu = FakeMenuActions()
        val coordinator = coordinator(resolution(listOf(action(1))), menu)

        coordinator.open(locator())
        coordinator.cancel()
        runCurrent()

        assertTrue(menu.events.isEmpty())
    }

    @Test
    fun cancellationBeforeSelectionResolutionPreventsExecution() = runTest {
        var executions = 0
        val menu = FakeMenuActions()
        val coordinator = coordinator(
            resolution(listOf(action(1))) { executions += 1; true },
            menu
        )

        coordinator.select(target(listOf(action(1))), 1)
        coordinator.cancel()
        runCurrent()

        assertEquals(0, executions)
        assertTrue(menu.events.isEmpty())
    }

    private fun TestScope.coordinator(
        resolution: NodeActionResolution,
        menu: FakeMenuActions
    ) = AccessibilityActionCoordinator(
        scope = this,
        resolver = NodeActionResolver { _, _ -> resolution },
        menuActions = menu
    )

    private fun resolution(
        actions: List<NodeActionDescriptor>,
        perform: (Int) -> Boolean = { true }
    ) = NodeActionResolution.Resolved(ResolvedNodeActionTarget(actions, perform))

    private fun action(id: Int) = NodeActionDescriptor(id, "Action $id")

    private fun target(actions: List<NodeActionDescriptor>) = NodeActionTarget(locator(), actions)

    private fun locator() = NodeActionLocator(
        identity = NodeActionIdentity(
            packageName = "app",
            windowId = 1,
            childPath = listOf(0),
            bounds = NodeActionBounds(0, 0, 100, 100),
            className = "Button",
            text = "Open",
            contentDescription = null,
            viewIdResourceName = "open",
            uniqueId = null
        ),
        selectionX = 50f,
        selectionY = 50f,
        reportedActionIds = setOf(1)
    )

    private class FakeMenuActions : AccessibilityActionMenuActions {
        val events = mutableListOf<String>()
        var lastTarget: NodeActionTarget? = null

        override suspend fun open(target: NodeActionTarget, isCurrent: () -> Boolean) {
            if (isCurrent()) {
                lastTarget = target
                events += "open:${target.actions.joinToString { it.id.toString() }}"
            }
        }

        override suspend fun replace(target: NodeActionTarget, isCurrent: () -> Boolean) {
            if (isCurrent()) {
                lastTarget = target
                events += "replace:${target.actions.joinToString { it.id.toString() }}"
            }
        }

        override suspend fun showMainWithoutActions(isCurrent: () -> Boolean) {
            if (isCurrent()) events += "main"
        }

        override suspend fun closeAll(isCurrent: () -> Boolean) {
            if (isCurrent()) events += "close"
        }
    }
}
