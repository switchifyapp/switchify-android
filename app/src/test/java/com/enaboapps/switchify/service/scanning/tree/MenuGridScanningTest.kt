package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.menu.MenuPageSections
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuGridScanningTest {
    private fun rows(): List<ScanTreeItem> {
        val sections = MenuPageSections(listOf(node("Actions")),
            listOf(node("Tap"), node("Home", 100), node("Scroll", 200), node("Settings")),
            listOf(null, node("Previous page", 100), node("Close", 200), null))
        return ExplicitScanRows.build(sections.rows(3, 4), true)
    }

    @Test fun explicitRowsDoNotMergeDespiteIdenticalVerticalCoordinates() {
        val tree = rows()
        assertEquals(listOf(1, 3, 1, 2), tree.map { it.children.size })
        assertEquals(listOf("Actions", "Tap", "Home", "Scroll", "Settings", "Previous page", "Close"),
            tree.flatMap { it.children }.map { it.getContentDescription() })
    }

    @Test fun sequentialScanTraversesContentThenNavigationExactlyOnce() {
        val navigator = ScanTreeNavigator(rows(), Settings(rowColumn = false))
        val visited = mutableListOf<String>()
        repeat(7) {
            visited.add(navigator.getCurrentNode()!!.getContentDescription())
            navigator.moveSelectionToNext()
        }
        assertEquals(listOf("Actions", "Tap", "Home", "Scroll", "Settings", "Previous page", "Close"), visited)
        assertFalse(navigator.handleEscape())
        assertEquals(0, navigator.currentColumn)
        assertEquals(1, navigator.currentCycle)
    }

    @Test fun rowColumnSelectsRowsBeforeTilesAndPreviousReverses() {
        val navigator = ScanTreeNavigator(rows(), Settings())
        assertEquals("Actions", navigator.getCurrentNode()!!.getContentDescription())
        navigator.moveSelectionToNext()
        assertEquals(1, navigator.currentTreeItem)
        navigator.isInTreeItem = true
        navigator.scanDirection = ScanDirection.RIGHT
        navigator.moveSelectionToNext()
        assertEquals("Home", navigator.getCurrentNode()!!.getContentDescription())
        navigator.moveSelectionToPrevious()
        assertEquals("Tap", navigator.getCurrentNode()!!.getContentDescription())
    }

    @Test fun threeTileRowDoesNotAddUnnecessaryGroupSelection() {
        assertFalse(rows()[1].isGrouped())
        assertTrue(rows()[0].isSingleNode())
    }

    @Test fun resetForPageOrSubmenuReturnStartsAtFirstItem() {
        val navigator = ScanTreeNavigator(rows(), Settings())
        navigator.moveSelectionToNext()
        navigator.isInTreeItem = true
        navigator.currentColumn = 2
        navigator.reset()
        assertEquals(0, navigator.currentTreeItem)
        assertEquals(0, navigator.currentColumn)
        assertFalse(navigator.isInTreeItem)
    }

    @Test fun oneNodeRowExecutesOnceWithoutSelectingAnotherContainer() {
        var selected = 0
        val item = ExplicitScanRows.build(listOf(emptyList(), listOf(node("Cancel", action = { selected++ }))), true).single()
        assertTrue(item.selectSingleNodeIfApplicable())
        assertEquals(1, selected)
    }

    @Test fun autoCycleLimitIsRetainedWhileManualDoesNotStopAtLimit() {
        for (automatic in listOf(true, false)) {
            val navigator = ScanTreeNavigator(rows(), Settings(automatic = automatic))
            navigator.currentCycle = 2
            assertEquals(automatic, navigator.isAutoScanCycleLimitReached())
        }
    }

    private class Settings(private val rowColumn: Boolean = true, private val automatic: Boolean = false) : ScanTreeNavigatorSettings {
        override fun isRowColumnScanEnabled() = rowColumn
        override fun isGroupScanEnabled() = true
        override fun getScanCycles() = 2
        override fun isAutoScanMode() = automatic
    }

    private fun node(name: String, left: Int = 0, action: () -> Unit = {}) = object : ScanNodeInterface {
        override fun getLeft() = left
        override fun getTop() = 0
        override fun getMidX() = left + 40
        override fun getMidY() = 40
        override fun getWidth() = 80
        override fun getHeight() = 80
        override fun getContentDescription() = name
        override fun highlight() = Unit
        override fun unhighlight() = Unit
        override fun select() = action()
    }
}
