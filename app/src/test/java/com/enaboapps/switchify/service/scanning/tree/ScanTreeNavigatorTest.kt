package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTreeNavigatorTest {
    @Test
    fun rowEndEntersEscapeStateWithoutWrappingToFirstNode() {
        val navigator = navigatorForRow("first", "last").apply {
            isInTreeItem = true
            scanDirection = ScanDirection.RIGHT
            currentColumn = 1
        }

        val moved = navigator.moveSelectionToNext()

        assertFalse(moved)
        assertTrue(navigator.handleEscape())
        assertEquals(1, navigator.currentColumn)
    }

    @Test
    fun confirmingRowEndEscapeLeavesCurrentRow() {
        val navigator = navigatorForRow("first", "last").apply {
            isInTreeItem = true
            scanDirection = ScanDirection.RIGHT
            currentColumn = 1
        }

        navigator.moveSelectionToNext()
        val confirmed = navigator.confirmEscape()

        assertTrue(confirmed)
        assertFalse(navigator.isInTreeItem)
        assertFalse(navigator.handleEscape())
    }

    @Test
    fun denyingRowEndEscapeStartsNextCycleAtFirstNode() {
        val navigator = navigatorForRow("first", "last").apply {
            isInTreeItem = true
            scanDirection = ScanDirection.RIGHT
            currentColumn = 1
        }

        navigator.moveSelectionToNext()
        val denied = navigator.denyEscape()

        assertTrue(denied)
        assertEquals(0, navigator.currentColumn)
        assertEquals(1, navigator.currentCycle)
        assertFalse(navigator.handleEscape())
    }

    private fun navigatorForRow(vararg ids: String): ScanTreeNavigator {
        val row = ScanTreeItem(
            children = ids.mapIndexed { index, id ->
                TestScanNode(id = id, nodeLeft = index * 100)
            },
            y = 0,
            isGroupScanEnabled = false
        )
        return ScanTreeNavigator(
            tree = listOf(row),
            scanSettings = TestNavigatorSettings()
        )
    }

    private class TestNavigatorSettings : ScanTreeNavigatorSettings {
        override fun isRowColumnScanEnabled(): Boolean = true
        override fun isDirectionalScanMode(): Boolean = false
        override fun isGroupScanEnabled(): Boolean = false
        override fun getScanCycles(): Int = 3
        override fun isAutoScanMode(): Boolean = false
    }

    private data class TestScanNode(
        val id: String,
        val nodeLeft: Int
    ) : ScanNodeInterface {
        override fun getLeft(): Int = nodeLeft
        override fun getTop(): Int = 0
        override fun getMidX(): Int = nodeLeft + 10
        override fun getMidY(): Int = 10
        override fun getWidth(): Int = 20
        override fun getHeight(): Int = 20
        override fun getContentDescription(): String = id
        override fun highlight() = Unit
        override fun unhighlight() = Unit
        override fun select() = Unit
    }
}
