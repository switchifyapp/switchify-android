package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CollectionRowGroupingTest {
    @Test
    fun keepsGeometryRowsWhenNoHints() {
        val row = listOf(node("a", left = 0, top = 0), node("b", left = 100, top = 0))

        val rows = CollectionRowGrouping.refineRows(listOf(row))

        assertEquals(1, rows.size)
        assertEquals(listOf("a", "b"), rows[0].ids())
    }

    @Test
    fun keepsGeometryRowsWhenOnlyOneHintedNode() {
        val row = listOf(
            node("a", left = 0, top = 0, rowIndex = 1),
            node("b", left = 100, top = 20)
        )

        val rows = CollectionRowGrouping.refineRows(listOf(row))

        assertEquals(1, rows.size)
        assertEquals(listOf("a", "b"), rows[0].ids())
    }

    @Test
    fun keepsGeometryRowsWhenHintCoverageTooLow() {
        val row = listOf(
            node("a", left = 0, top = 0, rowIndex = 1),
            node("b", left = 100, top = 0, rowIndex = 2),
            node("c", left = 200, top = 0),
            node("d", left = 300, top = 0)
        )

        val rows = CollectionRowGrouping.refineRows(listOf(row))

        assertEquals(1, rows.size)
        assertEquals(listOf("a", "b", "c", "d"), rows[0].ids())
    }

    @Test
    fun keepsGeometryRowsWhenAllHintsUseSameRowIndex() {
        val row = listOf(
            node("a", left = 100, top = 0, rowIndex = 1),
            node("b", left = 0, top = 0, rowIndex = 1)
        )

        val rows = CollectionRowGrouping.refineRows(listOf(row))

        assertEquals(1, rows.size)
        assertEquals(listOf("a", "b"), rows[0].ids())
    }

    @Test
    fun splitsGeometryRowWhenHintsExposeMultipleRows() {
        val row = listOf(
            node("a", left = 100, top = 0, rowIndex = 1),
            node("b", left = 0, top = 0, rowIndex = 1),
            node("c", left = 100, top = 40, rowIndex = 2),
            node("d", left = 0, top = 40, rowIndex = 2)
        )

        val rows = CollectionRowGrouping.refineRows(listOf(row))

        assertEquals(2, rows.size)
        assertEquals(listOf("b", "a"), rows[0].ids())
        assertEquals(listOf("d", "c"), rows[1].ids())
    }

    @Test
    fun sortsRefinedRowsByVisualTop() {
        val row = listOf(
            node("visuallySecond", left = 0, top = 40, rowIndex = 1),
            node("visuallyFirst", left = 0, top = 0, rowIndex = 2)
        )

        val rows = CollectionRowGrouping.refineRows(listOf(row))

        assertEquals(2, rows.size)
        assertEquals(listOf("visuallyFirst"), rows[0].ids())
        assertEquals(listOf("visuallySecond"), rows[1].ids())
    }

    @Test
    fun sortsNodesLeftToRightWithinRefinedRows() {
        val row = listOf(
            node("right", left = 200, top = 0, rowIndex = 1),
            node("left", left = 0, top = 0, rowIndex = 1),
            node("bottom", left = 0, top = 40, rowIndex = 2)
        )

        val rows = CollectionRowGrouping.refineRows(listOf(row))

        assertEquals(2, rows.size)
        assertEquals(listOf("left", "right"), rows[0].ids())
    }

    @Test
    fun ignoresInvalidNegativeRowIndex() {
        val row = listOf(
            node("invalid", left = 0, top = 0, rowIndex = -1),
            node("valid", left = 100, top = 0, rowIndex = 2)
        )

        val rows = CollectionRowGrouping.refineRows(listOf(row))

        assertEquals(1, rows.size)
        assertEquals(listOf("invalid", "valid"), rows[0].ids())
    }

    @Test
    fun doesNotMergeSeparateGeometryRows() {
        val firstRow = listOf(node("a", left = 0, top = 0, rowIndex = 1))
        val secondRow = listOf(node("b", left = 0, top = 40, rowIndex = 1))

        val rows = CollectionRowGrouping.refineRows(listOf(firstRow, secondRow))

        assertEquals(2, rows.size)
        assertSame(firstRow[0], rows[0][0])
        assertSame(secondRow[0], rows[1][0])
    }

    private fun node(
        id: String,
        left: Int,
        top: Int,
        rowIndex: Int? = null
    ): TestScanNode {
        return TestScanNode(
            id = id,
            nodeLeft = left,
            nodeTop = top,
            nodeWidth = 20,
            nodeHeight = 20,
            rowIndex = rowIndex
        )
    }

    private fun List<ScanNodeInterface>.ids(): List<String> {
        return map { (it as TestScanNode).id }
    }

    private data class TestScanNode(
        val id: String,
        val nodeLeft: Int,
        val nodeTop: Int,
        val nodeWidth: Int,
        val nodeHeight: Int,
        val rowIndex: Int?
    ) : ScanNodeInterface, CollectionRowHintProvider {
        override fun getCollectionRowHint(): CollectionRowHint? {
            return rowIndex?.let {
                CollectionRowHint(
                    rowIndex = it,
                    rowSpan = 1,
                    columnIndex = null,
                    columnSpan = null
                )
            }
        }

        override fun getLeft(): Int = nodeLeft
        override fun getTop(): Int = nodeTop
        override fun getMidX(): Int = nodeLeft + nodeWidth / 2
        override fun getMidY(): Int = nodeTop + nodeHeight / 2
        override fun getWidth(): Int = nodeWidth
        override fun getHeight(): Int = nodeHeight
        override fun getContentDescription(): String = id
        override fun highlight() = Unit
        override fun unhighlight() = Unit
        override fun select() = Unit
    }
}
