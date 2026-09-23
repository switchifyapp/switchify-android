package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object RowGroupingPolicy {
    private const val MIN_ROW_SIZE = 7
    private const val MIN_GROUP_SIZE = 2
    private const val MAX_GROUPS = 4
    private const val SIGNIFICANT_GAP_RATIO = 1.75

    fun split(
        sortedNodes: List<ScanNodeInterface>,
        enabled: Boolean
    ): List<List<ScanNodeInterface>> {
        if (!enabled || sortedNodes.size < MIN_ROW_SIZE) {
            return listOf(sortedNodes)
        }

        val groupCount = sqrt(sortedNodes.size.toDouble())
            .roundToInt()
            .coerceIn(2, minOf(MAX_GROUPS, sortedNodes.size / MIN_GROUP_SIZE))
        val balancedCuts = balancedCuts(sortedNodes.size, groupCount)
        val gaps = adjacentGaps(sortedNodes)
        val positiveGaps = gaps.filter { it > 0 }.sorted()
        val medianPositiveGap = positiveGaps.getOrNull(positiveGaps.size / 2) ?: 0
        val hasValidGeometry = sortedNodes.all { it.getWidth() > 0 }
        val cuts = mutableListOf<Int>()

        balancedCuts.forEachIndexed { index, balancedCut ->
            val remainingGroups = groupCount - index - 1
            val minimumCut = (cuts.lastOrNull() ?: 0) + MIN_GROUP_SIZE
            val maximumCut = sortedNodes.size - remainingGroups * MIN_GROUP_SIZE
            val candidates = (balancedCut - 1..balancedCut + 1)
                .filter { it in minimumCut..maximumCut }
            val bestCut = candidates.sortedWith(
                compareByDescending<Int> { gaps[it - 1] }
                    .thenBy { abs(it - balancedCut) }
                    .thenBy { it }
            ).firstOrNull() ?: balancedCut.coerceIn(minimumCut, maximumCut)
            val bestGap = gaps[bestCut - 1]
            val isSignificant = hasValidGeometry && if (medianPositiveGap == 0) {
                bestGap > 0
            } else {
                bestGap >= medianPositiveGap * SIGNIFICANT_GAP_RATIO
            }

            cuts += if (bestCut != balancedCut && isSignificant) bestCut else balancedCut
        }

        return buildGroups(sortedNodes, cuts)
    }

    private fun balancedCuts(itemCount: Int, groupCount: Int): List<Int> {
        val baseSize = itemCount / groupCount
        val remainder = itemCount % groupCount
        var offset = 0
        return List(groupCount - 1) { index ->
            offset += baseSize + if (index < remainder) 1 else 0
            offset
        }
    }

    private fun adjacentGaps(nodes: List<ScanNodeInterface>): List<Int> {
        return nodes.zipWithNext { current, next ->
            (next.getLeft() - (current.getLeft() + current.getWidth())).coerceAtLeast(0)
        }
    }

    private fun buildGroups(
        nodes: List<ScanNodeInterface>,
        cuts: List<Int>
    ): List<List<ScanNodeInterface>> {
        val boundaries = listOf(0) + cuts + nodes.size
        return boundaries.zipWithNext { start, end -> nodes.subList(start, end) }
    }
}
