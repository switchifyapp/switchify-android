package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RowGroupingPolicyTest {
    @Test
    fun disabledGroupingReturnsOneGroup() {
        val nodes = evenlySpacedNodes(10)

        val groups = RowGroupingPolicy.split(nodes, enabled = false)

        assertEquals(listOf(10), groups.map { it.size })
    }

    @Test
    fun rowsShorterThanSevenRemainFlat() {
        val groups = RowGroupingPolicy.split(evenlySpacedNodes(6), enabled = true)

        assertEquals(listOf(6), groups.map { it.size })
    }

    @Test
    fun groupCountScalesWithRowSize() {
        assertEquals(listOf(3, 2, 2), groupSizes(7))
        assertEquals(listOf(4, 3, 3), groupSizes(10))
        assertEquals(listOf(4, 4, 4), groupSizes(12))
        assertEquals(listOf(4, 4, 4, 4), groupSizes(16))
    }

    @Test
    fun pronouncedGapMovesNearbyBalancedCut() {
        val nodes = nodesWithGaps(listOf(10, 10, 100, 10, 10, 10, 10, 10, 10))

        val groups = RowGroupingPolicy.split(nodes, enabled = true)

        assertEquals(listOf(3, 4, 3), groups.map { it.size })
    }

    @Test
    fun smallSpacingDifferencesKeepBalancedCuts() {
        val nodes = nodesWithGaps(listOf(10, 11, 12, 10, 11, 12, 10, 11, 12))

        val groups = RowGroupingPolicy.split(nodes, enabled = true)

        assertEquals(listOf(4, 3, 3), groups.map { it.size })
    }

    @Test
    fun overlappingNodesKeepBalancedCuts() {
        val nodes = List(10) { index -> TestScanNode(index.toString(), index * 5, 10) }

        val groups = RowGroupingPolicy.split(nodes, enabled = true)

        assertEquals(listOf(4, 3, 3), groups.map { it.size })
    }

    @Test
    fun invalidNodeGeometryKeepsBalancedCuts() {
        val nodes = nodesWithGaps(listOf(10, 10, 100, 10, 10, 10, 10, 10, 10))
            .toMutableList()
        nodes[1] = nodes[1].copy(nodeWidth = 0)

        val groups = RowGroupingPolicy.split(nodes, enabled = true)

        assertEquals(listOf(4, 3, 3), groups.map { it.size })
    }

    @Test
    fun groupingPreservesEveryNodeInOrderWithoutSingletons() {
        for (itemCount in 7..40) {
            val nodes = evenlySpacedNodes(itemCount)
            val groups = RowGroupingPolicy.split(nodes, enabled = true)

            assertTrue(groups.all { it.size >= 2 })
            assertTrue(groups.size <= 4)
            assertEquals(nodes.size, groups.sumOf { it.size })
            nodes.forEachIndexed { index, node ->
                assertSame(node, groups.flatten()[index])
            }
        }
    }

    private fun groupSizes(itemCount: Int): List<Int> {
        return RowGroupingPolicy.split(evenlySpacedNodes(itemCount), enabled = true)
            .map { it.size }
    }

    private fun evenlySpacedNodes(itemCount: Int): List<TestScanNode> {
        return List(itemCount) { index -> TestScanNode(index.toString(), index * 20, 10) }
    }

    private fun nodesWithGaps(gaps: List<Int>): List<TestScanNode> {
        var left = 0
        return List(gaps.size + 1) { index ->
            TestScanNode(index.toString(), left, 10).also {
                if (index < gaps.size) {
                    left += 10 + gaps[index]
                }
            }
        }
    }

    private data class TestScanNode(
        val id: String,
        val nodeLeft: Int,
        val nodeWidth: Int
    ) : ScanNodeInterface {
        override fun getLeft(): Int = nodeLeft
        override fun getTop(): Int = 0
        override fun getMidX(): Int = nodeLeft + nodeWidth / 2
        override fun getMidY(): Int = 5
        override fun getWidth(): Int = nodeWidth
        override fun getHeight(): Int = 10
        override fun getContentDescription(): String = id
        override fun highlight() = Unit
        override fun unhighlight() = Unit
        override fun select() = Unit
    }
}
