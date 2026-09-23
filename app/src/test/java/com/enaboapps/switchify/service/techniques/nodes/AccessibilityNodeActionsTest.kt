package com.enaboapps.switchify.service.techniques.nodes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityNodeActionsTest {
    @Test
    fun resolvesUsefulAndCustomActionsInReportedOrder() {
        val result = NodeActionPolicy.resolve(
            actions = listOf(
                ReportedNodeAction(1, null),
                ReportedNodeAction(9001, "Archive"),
                ReportedNodeAction(2, "Open details"),
                ReportedNodeAction(1, null)
            ),
            standardLabel = { id -> mapOf(1 to "Click", 2 to "Long click")[id] },
            isExcluded = { false }
        )

        assertEquals(
            listOf(
                NodeActionDescriptor(1, "Click"),
                NodeActionDescriptor(9001, "Archive"),
                NodeActionDescriptor(2, "Open details")
            ),
            result
        )
    }

    @Test
    fun excludesInternalAndUnlabelledUnknownActions() {
        val result = NodeActionPolicy.resolve(
            actions = listOf(
                ReportedNodeAction(10, "Accessibility focus"),
                ReportedNodeAction(11, null),
                ReportedNodeAction(12, "Custom action")
            ),
            standardLabel = { null },
            isExcluded = { it == 10 }
        )

        assertEquals(listOf(NodeActionDescriptor(12, "Custom action")), result)
    }

    @Test
    fun choosesSmallestActionableNodeContainingPoint() {
        assertEquals(
            "child",
            NodeActionTargetSelector.selectSmallest(
                x = 50f,
                y = 50f,
                candidates = listOf(
                    NodeActionCandidate("parent", 0, 0, 100, 100, traversalOrder = 0),
                    NodeActionCandidate("child", 25, 25, 50, 50, traversalOrder = 1),
                    NodeActionCandidate("outside", 200, 200, 20, 20, traversalOrder = 2)
                )
            )
        )
    }

    @Test
    fun uniqueIdTakesPriority() {
        val locator = locator(identity(uniqueId = "stable", viewId = "shared"))
        val result = NodeActionLocatorMatcher.find(
            locator,
            listOf(
                identity(uniqueId = "other", viewId = "shared") to "view-id",
                identity(uniqueId = "stable", viewId = null) to "unique-id"
            )
        )

        assertEquals("unique-id", result)
    }

    @Test
    fun duplicateViewIdsUseClassAndClosestBounds() {
        val locator = locator(
            identity(viewId = "button", bounds = NodeActionBounds(100, 100, 200, 200))
        )
        val result = NodeActionLocatorMatcher.find(
            locator,
            listOf(
                identity(viewId = "button", bounds = NodeActionBounds(0, 0, 50, 50)) to "far",
                identity(viewId = "button", bounds = NodeActionBounds(105, 100, 205, 200)) to "near",
                identity(viewId = "button", className = "Text", bounds = locator.identity.bounds) to "wrong-class"
            )
        )

        assertEquals("near", result)
    }

    @Test
    fun verifiedChildPathMatchesWithoutIds() {
        val locator = locator(identity(childPath = listOf(1, 2), viewId = null, uniqueId = null))
        val result = NodeActionLocatorMatcher.find(
            locator,
            listOf(
                identity(childPath = listOf(1, 1), viewId = null, uniqueId = null) to "other",
                identity(childPath = listOf(1, 2), viewId = null, uniqueId = null) to "path"
            )
        )

        assertEquals("path", result)
    }

    @Test
    fun semanticFallbackRequiresTextOrDescription() {
        val locator = locator(
            identity(
                childPath = listOf(1),
                viewId = null,
                uniqueId = null,
                text = "Archive"
            )
        )
        val result = NodeActionLocatorMatcher.find(
            locator,
            listOf(
                identity(childPath = listOf(2), viewId = null, uniqueId = null, text = "Delete") to "wrong",
                identity(childPath = listOf(3), viewId = null, uniqueId = null, text = "Archive") to "semantic"
            )
        )

        assertEquals("semantic", result)
    }

    @Test
    fun boundsAloneNeverMatch() {
        val locator = locator(
            identity(childPath = listOf(1), viewId = null, uniqueId = null, text = null)
        )
        val candidate = identity(
            childPath = listOf(2),
            viewId = null,
            uniqueId = null,
            text = null,
            bounds = locator.identity.bounds
        )

        assertNull(NodeActionLocatorMatcher.find(locator, listOf(candidate to "replacement")))
    }

    @Test
    fun anotherPackageOrWindowNeverMatches() {
        val locator = locator(identity())

        assertNull(
            NodeActionLocatorMatcher.find(
                locator,
                listOf(
                    identity(packageName = "other.app") to "package",
                    identity(windowId = 8) to "window"
                )
            )
        )
    }

    private fun locator(identity: NodeActionIdentity) = NodeActionLocator(
        identity = identity,
        selectionX = 150f,
        selectionY = 150f,
        reportedActionIds = setOf(1)
    )

    private fun identity(
        packageName: String = "app",
        windowId: Int = 7,
        childPath: List<Int> = listOf(0),
        bounds: NodeActionBounds = NodeActionBounds(100, 100, 200, 200),
        className: String = "Button",
        text: String? = "Open",
        contentDescription: String? = null,
        viewId: String? = "open",
        uniqueId: String? = null
    ) = NodeActionIdentity(
        packageName = packageName,
        windowId = windowId,
        childPath = childPath,
        bounds = bounds,
        className = className,
        text = text,
        contentDescription = contentDescription,
        viewIdResourceName = viewId,
        uniqueId = uniqueId
    )
}
