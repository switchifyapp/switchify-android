package com.enaboapps.switchify.service.techniques.nodes

import com.enaboapps.switchify.service.techniques.pointscan.blocks.PointScanBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NodeTest {
    @Test
    fun scanSignatureIncludesContentDescription() {
        val first = testNode("Mouse")
        val second = testNode("Typing")

        assertNotEquals(first.scanSignature(), second.scanSignature())
    }

    @Test
    fun nodesWithSameBoundsAndDifferentContentAreNotEqual() {
        val first = testNode("Mouse")
        val second = testNode("Typing")

        assertNotEquals(first, second)
    }

    @Test
    fun nodesWithSameBoundsAndSameContentAreEqual() {
        val first = testNode("Mouse")
        val second = testNode("Mouse")

        assertEquals(first, second)
    }

    @Test
    fun equalNodesHaveEqualHashCodes() {
        assertEquals(testNode("Mouse").hashCode(), testNode("Mouse").hashCode())
    }

    @Test
    fun nodesWithDifferentContentHaveDifferentHashCodes() {
        assertNotEquals(testNode("Mouse").hashCode(), testNode("Typing").hashCode())
    }

    private fun testNode(contentDescription: String): Node {
        return Node.fromPointScanBlock(
            PointScanBlock(
                position = 0,
                row = 0,
                column = 0,
                left = 10,
                top = 20,
                right = 110,
                bottom = 120
            )
        ).apply {
            setContentDescription(contentDescription)
        }
    }
}
