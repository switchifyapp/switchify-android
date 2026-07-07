package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanTreeItemTest {
    @Test
    fun singleNodeRowUnhighlightOnlyUnhighlightsNode() {
        val boundsUi = TestBoundsUi()
        val node = TestScanNode(id = "single", nodeLeft = 0)
        val item = ScanTreeItem(
            children = listOf(node),
            y = 0,
            isGroupScanEnabled = false,
            boundsUi = boundsUi
        )

        item.unhighlight()

        assertEquals(1, node.unhighlightCount)
        assertEquals(0, boundsUi.hideAllCount)
    }

    @Test
    fun rowUnhighlightUnhighlightsEachNodeAndClearsBounds() {
        val boundsUi = TestBoundsUi()
        val nodes = nodes("a", "b", "c")
        val item = ScanTreeItem(
            children = nodes,
            y = 0,
            isGroupScanEnabled = false,
            boundsUi = boundsUi
        )

        item.unhighlight()

        assertEquals(listOf(1, 1, 1), nodes.map { it.unhighlightCount })
        assertEquals(1, boundsUi.hideAllCount)
    }

    @Test
    fun groupUnhighlightOnlyUnhighlightsActiveGroupAndClearsBounds() {
        val boundsUi = TestBoundsUi()
        val nodes = nodes("a", "b", "c", "d")
        val item = ScanTreeItem(
            children = nodes,
            y = 0,
            isGroupScanEnabled = true,
            boundsUi = boundsUi
        )

        item.unhighlight(groupIndex = 1)

        assertEquals(listOf(0, 0, 1, 1), nodes.map { it.unhighlightCount })
        assertEquals(1, boundsUi.hideAllCount)
    }

    @Test
    fun escapeUnhighlightUnhighlightsNodesAndClearsBounds() {
        val boundsUi = TestBoundsUi()
        val nodes = nodes("a", "b")
        val item = ScanTreeItem(
            children = nodes,
            y = 0,
            isGroupScanEnabled = false,
            boundsUi = boundsUi
        )

        item.unhighlightEscape()

        assertEquals(listOf(1, 1), nodes.map { it.unhighlightCount })
        assertEquals(1, boundsUi.hideAllCount)
    }

    private fun nodes(vararg ids: String): List<TestScanNode> {
        return ids.mapIndexed { index, id ->
            TestScanNode(id = id, nodeLeft = index * 100)
        }
    }

    private class TestBoundsUi : ScanTreeBoundsUi {
        var hideAllCount = 0

        override fun showRowBounds(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            target: OverlayTarget
        ) = Unit

        override fun showEscapeBounds(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            target: OverlayTarget
        ) = Unit

        override fun hideAll() {
            hideAllCount++
        }
    }

    private data class TestScanNode(
        val id: String,
        val nodeLeft: Int
    ) : ScanNodeInterface {
        var unhighlightCount = 0

        override fun getLeft(): Int = nodeLeft
        override fun getTop(): Int = 0
        override fun getMidX(): Int = nodeLeft + 10
        override fun getMidY(): Int = 10
        override fun getWidth(): Int = 20
        override fun getHeight(): Int = 20
        override fun getContentDescription(): String = id
        override fun highlight() = Unit
        override fun unhighlight() {
            unhighlightCount++
        }
        override fun select() = Unit
    }
}
