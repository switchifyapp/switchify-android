package com.enaboapps.switchify.service.techniques.nodes

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeContainmentFilterTest {
    @Test
    fun matchesPairwiseReferenceWithoutChangingOrderOrDuplicateBounds() {
        val random = Random(2836)
        repeat(100) {
            val nodes = List(100) {
                NodeRectangle(random.nextInt(50), random.nextInt(50), random.nextInt(1, 50), random.nextInt(1, 50))
            } + List(4) { NodeRectangle(5, 5, 3, 3) }
            val expected = nodes.filter { outer -> nodes.none { inner -> inner != outer && outer.contains(inner) } }
            assertEquals(expected, filterContainedNodes(nodes) { it })
        }
    }

    @Test
    fun disjointThousandNodeRowAvoidsPairwiseComparisons() {
        val nodes = List(1000) { NodeRectangle(it * 10, 0, 5, 5) }
        var checks = 0
        assertEquals(nodes, filterContainedNodes(nodes, { checks++ }) { it })
        assertTrue("Visited $checks index branches", checks < 30_000)
    }

    @Test
    fun equalBoundsAndExtremeCoordinatesRemainSafe() {
        val same = NodeRectangle(Int.MAX_VALUE - 10, 0, 20, 20)
        val smaller = NodeRectangle(Int.MAX_VALUE - 5, 1, 5, 5)
        assertEquals(listOf(same, same), filterContainedNodes(listOf(same, same)) { it })
        assertEquals(listOf(smaller), filterContainedNodes(listOf(same, smaller)) { it })
    }
}
