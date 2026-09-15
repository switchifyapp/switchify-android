package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import org.junit.Assert.*
import org.junit.Test

class ScanTreeReconciliationTest {
    private class TestNode(
        val label: String,
        override val scanIdentity: ScanNodeIdentity
    ) : ScanNodeInterface, ScanNodeIdentityProvider {
        var selections = 0
        override fun getLeft() = 0
        override fun getTop() = 0
        override fun getMidX() = 0
        override fun getMidY() = 0
        override fun getWidth() = 10
        override fun getHeight() = 10
        override fun getContentDescription() = label
        override fun highlight() = Unit
        override fun unhighlight() = Unit
        override fun select() { selections++ }
    }

    private fun node(id: String, path: Int = 0, label: String = id, source: String = "app:0:1") =
        TestNode(label, ScanNodeIdentity(source, id, null, "button", listOf(path)))

    private class Settings(val rows: Boolean = false, val groups: Boolean = false) : ScanTreeNavigatorSettings {
        override fun isRowColumnScanEnabled() = rows
        override fun isGroupScanEnabled() = groups
        override fun getScanCycles() = 3
        override fun isAutoScanMode() = true
    }

    private fun row(nodes: List<ScanNodeInterface>, grouped: Boolean = false) = ScanTreeItem(nodes, 0, grouped)

    @Test fun changedLabelsAndPathsKeepStableIdentity() {
        val old = node("stable", 0, "old")
        val fresh = node("stable", 2, "new")
        assertSame(fresh, matchScanNodes(listOf(old), listOf(fresh))[old])
    }

    @Test fun differentWindowsAndConflictingIdsCannotMatchByPath() {
        val old = node("old")
        assertTrue(matchScanNodes(listOf(old), listOf(node("new"))).isEmpty())
        assertTrue(matchScanNodes(listOf(old), listOf(node("old", source = "app:0:2"))).isEmpty())
    }

    @Test fun ambiguousIdsDoNotChooseAnArbitraryNode() {
        val old = node("duplicate")
        assertTrue(matchScanNodes(listOf(old), listOf(node("duplicate"), node("duplicate"))).isEmpty())
    }

    @Test fun resourceAndStructuralFallbacksAreOneToOne() {
        val old = TestNode("old", ScanNodeIdentity("app", null, "view", "button", listOf(0)))
        val moved = TestNode("new", old.scanIdentity.copy(path = listOf(4)))
        assertSame(moved, matchScanNodes(listOf(old), listOf(moved))[old])
        val anonymous = TestNode("old", old.scanIdentity.copy(resourceId = null))
        val renamed = TestNode("new", anonymous.scanIdentity)
        assertSame(renamed, matchScanNodes(listOf(anonymous), listOf(renamed))[anonymous])
    }

    @Test fun insertionAndReorderingKeepCurrentTargetAndCycleProgress() {
        val old = listOf(node("a"), node("b"), node("c"))
        val tree = mutableListOf(row(old))
        val navigator = ScanTreeNavigator(tree, Settings()).apply {
            currentColumn = 1
            scanDirection = ScanDirection.RIGHT
            currentCycle = 2
        }
        val fresh = listOf(node("c"), node("inserted"), node("b"), node("a"))
        val replacement = listOf(row(fresh))
        assertTrue(navigator.reconcile(replacement, matchScanNodes(old, fresh)))
        tree.clear(); tree.addAll(replacement)
        assertSame(fresh[2], navigator.getCurrentNode())
        assertEquals(2, navigator.currentCycle)
        navigator.getCurrentNode()!!.select()
        assertEquals(1, fresh[2].selections)
        assertEquals(0, old[1].selections)
    }

    @Test fun removalUsesOldScanOrderInBothDirections() {
        for (reverse in listOf(false, true)) {
            val old = listOf(node("a"), node("b"), node("c"), node("d"))
            val tree = mutableListOf(row(old))
            val navigator = ScanTreeNavigator(tree, Settings()).apply {
                currentColumn = 1
                scanDirection = if (reverse) ScanDirection.LEFT else ScanDirection.RIGHT
            }
            val fresh = listOf(node("d"), node("inserted"), node("a"), node("c"))
            val replacement = listOf(row(fresh))
            assertFalse(navigator.reconcile(replacement, matchScanNodes(old, fresh)))
            tree.clear(); tree.addAll(replacement)
            assertEquals(if (reverse) "a" else "c", navigator.getCurrentNode()!!.getContentDescription())
        }
    }

    @Test fun rowSplitKeepsContainerWithMostSurvivingMembers() {
        val old = listOf(node("a"), node("b"), node("c"))
        val tree = mutableListOf(row(old))
        val navigator = ScanTreeNavigator(tree, Settings(rows = true))
        val fresh = listOf(node("a"), node("b"), node("c"))
        val replacement = listOf(row(fresh.take(1)), row(fresh.drop(1)))
        assertTrue(navigator.reconcile(replacement, matchScanNodes(old, fresh)))
        assertEquals(1, navigator.currentTreeItem)
        assertFalse(navigator.isInTreeItem)
    }

    @Test fun escapeAndCycleBreakSurviveContentRefresh() {
        val old = listOf(node("a"), node("b"))
        val tree = mutableListOf(row(old))
        val navigator = ScanTreeNavigator(tree, Settings(rows = true)).apply {
            isInTreeItem = true
            scanDirection = ScanDirection.RIGHT
            currentColumn = 1
        }
        navigator.moveSelectionToNext()
        assertTrue(navigator.handleEscape())
        val fresh = listOf(node("a"), node("b"))
        assertTrue(navigator.reconcile(listOf(row(fresh)), matchScanNodes(old, fresh)))
        assertTrue(navigator.handleEscape())
        navigator.isInCycleBreak = true
        navigator.currentCycle = 2
        assertTrue(navigator.reconcile(listOf(row(fresh)), matchScanNodes(old, fresh)))
        assertTrue(navigator.isInCycleBreak)
        assertEquals(2, navigator.currentCycle)
    }

    @Test fun groupedNodeKeepsItsDepthAfterRegrouping() {
        val old = (0..8).map { node("$it") }
        val tree = mutableListOf(row(old, true))
        val navigator = ScanTreeNavigator(tree, Settings(rows = true, groups = true)).apply {
            isInTreeItem = true
            isInGroup = true
            isScanningGroups = false
            currentGroup = 1
            currentColumn = 1
            currentCycle = 2
        }
        val current = navigator.getCurrentNode()!!.getContentDescription()
        val fresh = (0..9).map { node("$it") }
        val replacement = listOf(row(fresh, true))
        assertTrue(navigator.reconcile(replacement, matchScanNodes(old, fresh)))
        tree.clear(); tree.addAll(replacement)
        assertEquals(current, navigator.getCurrentNode()!!.getContentDescription())
        assertTrue(navigator.isInGroup)
        assertTrue(navigator.isInTreeItem)
        assertFalse(navigator.isScanningGroups)
        assertEquals(2, navigator.currentCycle)
    }

    @Test fun removedLastTargetWrapsAndTotalReplacementStartsAtDirectionBoundary() {
        val old = listOf(node("a"), node("b"))
        val tree = mutableListOf(row(old))
        val navigator = ScanTreeNavigator(tree, Settings()).apply {
            currentColumn = 1
            scanDirection = ScanDirection.RIGHT
        }
        val fresh = listOf(node("new"), node("a"))
        assertFalse(navigator.reconcile(listOf(row(fresh)), matchScanNodes(old, fresh)))
        assertEquals(1, navigator.currentColumn)
        navigator.scanDirection = ScanDirection.LEFT
        assertFalse(navigator.reconcile(listOf(row(listOf(node("x"), node("y")))), emptyMap()))
        assertEquals(1, navigator.currentColumn)
    }
}
