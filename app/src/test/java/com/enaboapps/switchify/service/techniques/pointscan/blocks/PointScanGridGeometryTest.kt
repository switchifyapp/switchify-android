package com.enaboapps.switchify.service.techniques.pointscan.blocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointScanGridGeometryTest {
    private val supportedSizes = listOf(2, 3, 4, 5, 6)

    @Test
    fun blocksTileTheGridInReadingOrderWithoutGapsOrOverlap() {
        supportedSizes.forEach { size ->
            val blocks = PointScanGridGeometry.blocks(1080, 2340, size)
            assertEquals(size * size, blocks.size)
            blocks.forEachIndexed { index, block ->
                assertEquals(index, block.position)
                assertEquals(index / size, block.row)
                assertEquals(index % size, block.column)
                assertEquals(1080 / size, block.width)
                assertEquals(2340 / size, block.height)
                if (block.column > 0) assertEquals(blocks[index - 1].right, block.left)
                if (block.row > 0) assertEquals(blocks[index - size].bottom, block.top)
            }
            assertEquals(0, blocks.first().left)
            assertEquals(0, blocks.first().top)
        }
    }

    @Test
    fun gridLinesFallOnBlockEdges() {
        supportedSizes.forEach { size ->
            val blocks = PointScanGridGeometry.blocks(1080, 2340, size)
            val xs = PointScanGridGeometry.lineOffsets(1080, size)
            val ys = PointScanGridGeometry.lineOffsets(2340, size)
            assertEquals(size - 1, xs.size)
            assertEquals(size - 1, ys.size)
            assertEquals(blocks.filter { it.column > 0 }.map { it.left }.distinct(), xs)
            assertEquals(blocks.filter { it.row > 0 }.map { it.top }.distinct(), ys)
        }
    }

    @Test
    fun unevenScreensLeaveTheRemainderOutsideTheGrid() {
        val blocks = PointScanGridGeometry.blocks(1000, 1000, 3)
        assertEquals(999, blocks.last().right)
        assertEquals(999, blocks.last().bottom)
        assertTrue(blocks.all { it.width == 333 && it.height == 333 })
        assertEquals(999, PointScanGridGeometry.blockWidth(1000, 3) * 3)
    }
}
