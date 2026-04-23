package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import kotlin.math.atan2

/**
 * Builds a flat, clockwise-ordered scanning tree for radial menu pages.
 *
 * Each node becomes its own [ScanTreeItem] (single-child), and the items are
 * sorted by the polar angle of their midpoint around the supplied centre so
 * that auto-scan sweeps from 12 o'clock round the ring. The centre node is
 * excluded from the angular sort and placed at the end of the tree so one
 * revolution around the ring lands on the centre naturally.
 */
class RadialScanTreeBuilder {

    /**
     * @param nodes All scannable nodes on the page (ring items + centre + any
     *              nav/manipulator items rendered outside the ring).
     * @param centerX X coordinate (screen px) of the ring's centre, used as the
     *                origin for angular sort.
     * @param centerY Y coordinate (screen px) of the ring's centre.
     * @param centerNode The node sitting at the centre of the ring (usually the
     *                   close-menu manipulator). Placed at the end of the tree.
     *                   Pass null if no centre node exists.
     * @param trailingNodes Any nodes rendered outside the ring (e.g. prev/next
     *                      page buttons) that should be scanned after the
     *                      centre. Order preserved.
     */
    fun buildTree(
        nodes: List<ScanNodeInterface>,
        centerX: Int,
        centerY: Int,
        centerNode: ScanNodeInterface?,
        trailingNodes: List<ScanNodeInterface> = emptyList()
    ): List<ScanTreeItem> {
        val ringNodes = nodes.filter { node ->
            node !== centerNode && trailingNodes.none { it === node }
        }

        // Sort by polar angle: -π/2 (12 o'clock) → +π (clockwise back round).
        // Rotate by +π/2 and wrap into [0, 2π) so 12 o'clock is first and angles
        // increase clockwise (screen y grows downward, so atan2 already gives
        // clockwise order).
        val sortedRing = ringNodes.sortedBy { node ->
            val raw = atan2(
                (node.getMidY() - centerY).toDouble(),
                (node.getMidX() - centerX).toDouble()
            )
            val rotated = raw + Math.PI / 2.0
            ((rotated % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2)
        }

        val tree = mutableListOf<ScanTreeItem>()
        sortedRing.forEach { tree.add(singleNodeItem(it)) }
        centerNode?.let { tree.add(singleNodeItem(it)) }
        trailingNodes.forEach { tree.add(singleNodeItem(it)) }
        return tree
    }

    private fun singleNodeItem(node: ScanNodeInterface): ScanTreeItem {
        return ScanTreeItem(listOf(node), node.getTop(), false)
    }
}
