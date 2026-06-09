package com.enaboapps.switchify.service.techniques.nodes.scanners

import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.pointscan.blocks.PointScanBlock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseNodeScannerTest {
    @Test
    fun sameNodeSignaturesAreDuplicate() {
        val previous = listOf(testNode("Mouse"))
        val next = listOf(testNode("Mouse"))

        assertTrue(areDuplicateScanNodes(previous, next))
    }

    @Test
    fun changedContentWithSameBoundsIsNotDuplicate() {
        val previous = listOf(testNode("Mouse"))
        val next = listOf(testNode("Typing"))

        assertFalse(areDuplicateScanNodes(previous, next))
    }

    @Test
    fun differentNodeCountsAreNotDuplicate() {
        val previous = listOf(testNode("Mouse"))
        val next = listOf(testNode("Mouse"), testNode("Typing"))

        assertFalse(areDuplicateScanNodes(previous, next))
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
