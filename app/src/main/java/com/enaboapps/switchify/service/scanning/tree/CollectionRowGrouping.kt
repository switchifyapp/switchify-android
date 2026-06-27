package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanNodeInterface

internal object CollectionRowGrouping {
    fun refineRows(
        geometryRows: List<List<ScanNodeInterface>>,
        minimumHintedNodes: Int = 2,
        minimumHintCoverage: Double = 0.75
    ): List<List<ScanNodeInterface>> {
        require(minimumHintedNodes > 0)
        require(minimumHintCoverage > 0.0 && minimumHintCoverage <= 1.0)

        return geometryRows.flatMap { row ->
            refineRow(
                row = row,
                minimumHintedNodes = minimumHintedNodes,
                minimumHintCoverage = minimumHintCoverage
            )
        }
    }

    private fun refineRow(
        row: List<ScanNodeInterface>,
        minimumHintedNodes: Int,
        minimumHintCoverage: Double
    ): List<List<ScanNodeInterface>> {
        if (row.isEmpty()) return listOf(row)

        val hintedNodes = row.mapNotNull { node ->
            val hint = (node as? CollectionRowHintProvider)?.getCollectionRowHint()
            if (hint != null && hint.rowIndex >= 0) {
                HintedNode(node, hint)
            } else {
                null
            }
        }
        val hintCoverage = hintedNodes.size.toDouble() / row.size.toDouble()
        val distinctRowIndexes = hintedNodes.map { it.hint.rowIndex }.distinct()

        if (
            hintedNodes.size < minimumHintedNodes ||
            hintCoverage < minimumHintCoverage ||
            distinctRowIndexes.size < 2
        ) {
            return listOf(row)
        }

        val grouped = hintedNodes
            .groupBy { it.hint.rowIndex }
            .mapValues { (_, nodes) -> nodes.map { it.node }.toMutableList() }
            .toMutableMap()

        val visualRows = grouped.mapValues { (_, nodes) -> nodes.averageMidY() }
        val unhintedNodes = row.filterNot { node -> hintedNodes.any { it.node === node } }
        unhintedNodes.forEach { node ->
            val nearestRowIndex = visualRows.minByOrNull { (_, visualMidY) ->
                kotlin.math.abs(node.getMidY() - visualMidY)
            }?.key

            if (nearestRowIndex == null) return listOf(row)
            grouped.getValue(nearestRowIndex).add(node)
        }

        return grouped.entries
            .sortedWith(
                compareBy<Map.Entry<Int, MutableList<ScanNodeInterface>>> { (_, nodes) ->
                    nodes.minOf { it.getTop() }
                }.thenBy { (rowIndex, _) -> rowIndex }
            )
            .map { (_, nodes) -> nodes.sortedBy { it.getLeft() } }
    }

    private data class HintedNode(
        val node: ScanNodeInterface,
        val hint: CollectionRowHint
    )

    private fun List<ScanNodeInterface>.averageMidY(): Double {
        return sumOf { it.getMidY() }.toDouble() / size.toDouble()
    }
}
