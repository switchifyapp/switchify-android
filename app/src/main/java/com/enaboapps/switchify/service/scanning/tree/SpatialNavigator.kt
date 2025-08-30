package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import kotlin.math.abs

/**
 * Handles spatial navigation for directional scanning mode.
 * Finds the closest nodes in each direction based on screen coordinates.
 */
class SpatialNavigator(private val tree: List<ScanTreeItem>) {

    /**
     * All nodes flattened from the tree with their spatial information
     */
    private val allNodes: List<SpatialNode> by lazy {
        tree.flatMapIndexed { treeIndex, item ->
            item.children.mapIndexed { nodeIndex, node ->
                SpatialNode(
                    node = node,
                    treeItemIndex = treeIndex,
                    nodeIndex = nodeIndex,
                    centerX = node.getLeft() + node.getWidth() / 2,
                    centerY = node.getTop() + node.getHeight() / 2
                )
            }
        }
    }

    /**
     * Find the closest node in the specified direction from the current position
     * @param currentTreeIndex Current tree item index
     * @param currentNodeIndex Current node index within the tree item
     * @param direction The direction to move in
     * @return Pair of (treeIndex, nodeIndex) for the closest node, or null if none found
     */
    fun findClosestNodeInDirection(
        currentTreeIndex: Int,
        currentNodeIndex: Int,
        direction: ScanDirection
    ): Pair<Int, Int>? {
        if (tree.isEmpty() || currentTreeIndex >= tree.size) return null

        val currentItem = tree[currentTreeIndex]
        if (currentNodeIndex >= currentItem.children.size) return null

        val currentNode = currentItem.children[currentNodeIndex]
        val currentCenterX = currentNode.getLeft() + currentNode.getWidth() / 2
        val currentCenterY = currentNode.getTop() + currentNode.getHeight() / 2

        // Find all candidate nodes in the specified direction
        val candidates = allNodes.filter { spatialNode ->
            // Don't include the current node
            if (spatialNode.treeItemIndex == currentTreeIndex &&
                spatialNode.nodeIndex == currentNodeIndex
            ) {
                return@filter false
            }

            when (direction) {
                ScanDirection.UP -> spatialNode.centerY < currentCenterY
                ScanDirection.DOWN -> spatialNode.centerY > currentCenterY
                ScanDirection.LEFT -> spatialNode.centerX < currentCenterX
                ScanDirection.RIGHT -> spatialNode.centerX > currentCenterX
            }
        }

        if (candidates.isEmpty()) return null

        // Find the closest candidate based on direction-specific logic
        val closest = when (direction) {
            ScanDirection.UP, ScanDirection.DOWN -> {
                findClosestVerticalNode(currentCenterX, currentCenterY, candidates, direction)
            }

            ScanDirection.LEFT, ScanDirection.RIGHT -> {
                findClosestHorizontalNode(currentCenterX, currentCenterY, candidates, direction)
            }
        }

        return closest?.let { Pair(it.treeItemIndex, it.nodeIndex) }
    }

    /**
     * Find the closest node for vertical movement (UP/DOWN)
     */
    private fun findClosestVerticalNode(
        currentCenterX: Int,
        currentCenterY: Int,
        candidates: List<SpatialNode>,
        direction: ScanDirection
    ): SpatialNode? {
        return candidates.minByOrNull { candidate ->
            val verticalDistance = abs(candidate.centerY - currentCenterY)
            val horizontalDistance = abs(candidate.centerX - currentCenterX)

            // Prioritize nodes that are more directly above/below
            // Weight horizontal distance more heavily to prefer direct vertical alignment
            verticalDistance + (horizontalDistance * 2)
        }
    }

    /**
     * Find the closest node for horizontal movement (LEFT/RIGHT)
     */
    private fun findClosestHorizontalNode(
        currentCenterX: Int,
        currentCenterY: Int,
        candidates: List<SpatialNode>,
        direction: ScanDirection
    ): SpatialNode? {
        return candidates.minByOrNull { candidate ->
            val horizontalDistance = abs(candidate.centerX - currentCenterX)
            val verticalDistance = abs(candidate.centerY - currentCenterY)

            // Prioritize nodes that are more directly left/right
            // Weight vertical distance more heavily to prefer direct horizontal alignment
            horizontalDistance + (verticalDistance * 2)
        }
    }

    /**
     * Get the first node for initial positioning
     * @return Pair of (treeIndex, nodeIndex) for the first node, or null if tree is empty
     */
    fun getFirstNode(): Pair<Int, Int>? {
        return if (tree.isNotEmpty() && tree[0].children.isNotEmpty()) {
            Pair(0, 0)
        } else {
            null
        }
    }

    /**
     * Data class representing a node with its spatial information
     */
    private data class SpatialNode(
        val node: ScanNodeInterface,
        val treeItemIndex: Int,
        val nodeIndex: Int,
        val centerX: Int,
        val centerY: Int
    )
}