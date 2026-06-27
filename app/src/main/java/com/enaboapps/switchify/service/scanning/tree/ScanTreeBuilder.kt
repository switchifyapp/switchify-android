package com.enaboapps.switchify.service.scanning.tree

import android.content.Context
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.utils.ScreenUtils
import kotlin.math.abs

/**
 * This class is responsible for building the ScanTree structure.
 * It organizes ScanNodeInterface objects into ScanTreeItems based on their positions and scan settings.
 *
 * @property context The application context.
 * @property scanSettings The settings for scanning behavior.
 */
class ScanTreeBuilder(
    private val context: Context,
    private val scanSettings: ScanSettings
) {
    /**
     * Builds the scanning tree from a list of scan nodes.
     *
     * @param nodes The list of ScanNodeInterface objects to build the tree from.
     * @param itemThreshold The threshold for determining if a node is in the same item (in dp).
     * @return A list of ScanTreeItems representing the built tree.
     */
    fun buildTree(nodes: List<ScanNodeInterface>, itemThreshold: Int = 40): List<ScanTreeItem> {
        if (nodes.isEmpty()) return emptyList()

        val geometryRows = buildGeometryRows(nodes, itemThreshold)
        val rows = if (scanSettings.isRowColumnScanEnabled()) {
            CollectionRowGrouping.refineRows(geometryRows)
        } else {
            geometryRows
        }

        return rows.map { createScanTreeItem(it) }
    }

    private fun buildGeometryRows(
        nodes: List<ScanNodeInterface>,
        itemThreshold: Int
    ): List<List<ScanNodeInterface>> {
        val rows = mutableListOf<List<ScanNodeInterface>>()

        val sortedNodes = nodes.sortedBy { it.getMidY() }
        var currentTreeItem = mutableListOf<ScanNodeInterface>(sortedNodes.first())
        var currentYBaseline = sortedNodes.first().getMidY()
        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)

        for (node in sortedNodes.drop(1)) {
            if (shouldAddNodeToCurrentItem(
                    node,
                    currentYBaseline,
                    itemThreshold,
                    screenWidth,
                    screenHeight
                )
            ) {
                currentTreeItem.add(node)
            } else {
                if (currentTreeItem.isNotEmpty()) {
                    rows.add(currentTreeItem)
                    currentTreeItem = mutableListOf()
                }
                currentTreeItem.add(node)
                currentYBaseline = node.getMidY()
            }
        }

        if (currentTreeItem.isNotEmpty()) {
            rows.add(currentTreeItem)
        }

        return rows
    }

    /**
     * Determines if a node should be added to the current tree item based on its position and size.
     *
     * @param node The node to check.
     * @param currentYBaseline The current Y baseline for comparison.
     * @param itemThreshold The threshold for Y position difference.
     * @param screenWidth The width of the screen.
     * @param screenHeight The height of the screen.
     * @return True if the node should be added to the current item, false otherwise.
     */
    private fun shouldAddNodeToCurrentItem(
        node: ScanNodeInterface,
        currentYBaseline: Int,
        itemThreshold: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val yDifference = abs(node.getMidY() - currentYBaseline)
        val isCloseEnough = yDifference <= ScreenUtils.dpToPx(context, itemThreshold)
        val width = node.getWidth()
        val height = node.getHeight()
        val isCloseToFullScreen = width > 0.8 * screenWidth && height > 0.8 * screenHeight
        val isBiggerThanZero = width > 0 && height > 0

        return isCloseEnough && !isCloseToFullScreen && isBiggerThanZero
    }

    /**
     * Creates a ScanTreeItem from a list of ScanNodeInterface objects.
     *
     * @param children The list of ScanNodeInterface objects to create the item from.
     * @return A ScanTreeItem containing the organized nodes.
     */
    private fun createScanTreeItem(children: List<ScanNodeInterface>): ScanTreeItem {
        val sorted = children.sortedBy { it.getLeft() }
        return if (scanSettings.isRowColumnScanEnabled()) {
            val isGroupScanEnabled = scanSettings.isGroupScanEnabled()
            ScanTreeItem(sorted, sorted[0].getTop(), isGroupScanEnabled)
        } else {
            ScanTreeItem(sorted, sorted[0].getTop(), false)
        }
    }
}
