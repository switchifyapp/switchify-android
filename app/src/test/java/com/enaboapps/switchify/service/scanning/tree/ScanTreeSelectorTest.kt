package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTreeSelectorTest {
    @Test
    fun groupedSelectionExecutesTheNodeFromTheSelectedGroup() {
        val nodes = List(9) { index -> TestScanNode(index.toString(), index * 100) }
        val item = ScanTreeItem(nodes, y = 0, isGroupScanEnabled = true)
        val settings = TestSettings()
        val navigator = ScanTreeNavigator(listOf(item), settings)
        val selector = ScanTreeSelector(
            tree = listOf(item),
            navigator = navigator,
            scanSettings = settings,
            stopScanningOnSelect = true
        )

        assertFalse(selector.performSelection())
        assertTrue(navigator.isInTreeItem)
        navigator.moveSelectionToNext()
        assertEquals(1, navigator.currentGroup)
        assertFalse(selector.performSelection())
        assertTrue(navigator.isInGroup)
        navigator.moveSelectionToNext()
        navigator.moveSelectionToNext()

        assertTrue(selector.performSelection())

        assertEquals(listOf(0, 0, 0, 0, 0, 1, 0, 0, 0), nodes.map { it.selectCount })
        assertFalse(navigator.isInTreeItem)
    }

    private class TestSettings : ScanTreeNavigatorSettings, ScanTreeSelectorSettings {
        override fun isRowColumnScanEnabled(): Boolean = true
        override fun isGroupScanEnabled(): Boolean = true
        override fun getScanCycles(): Int = 3
        override fun isAutoScanMode(): Boolean = false
    }

    private data class TestScanNode(
        val id: String,
        val nodeLeft: Int
    ) : ScanNodeInterface {
        var selectCount = 0

        override fun getLeft(): Int = nodeLeft
        override fun getTop(): Int = 0
        override fun getMidX(): Int = nodeLeft + 10
        override fun getMidY(): Int = 10
        override fun getWidth(): Int = 20
        override fun getHeight(): Int = 20
        override fun getContentDescription(): String = id
        override fun highlight() = Unit
        override fun unhighlight() = Unit
        override fun select() {
            selectCount++
        }
    }
}
