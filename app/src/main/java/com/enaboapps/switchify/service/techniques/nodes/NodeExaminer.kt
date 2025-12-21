package com.enaboapps.switchify.service.techniques.nodes

import android.content.Context
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.keyboard.KeyboardNodeExtractor
import com.enaboapps.switchify.service.utils.ScreenUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.LinkedList
import java.util.Queue
import kotlin.math.sqrt

/**
 * NodeExaminer is responsible for examining accessibility nodes within an application's UI.
 * It provides methods to find, filter, and analyze nodes, as well as to observe changes in the node structure.
 * This implementation includes deep content description analysis for nodes with empty content
 * and filtering to identify the smallest nested nodes for all UI elements.
 */
object NodeExaminer {
    private const val TAG = "NodeExaminer"
    private const val TREE_PROCESSING_TIMEOUT_MS = 5000L
    private const val MAX_NODES_THRESHOLD = 1000

    /** Keyboard node extractor for handling keyboard-specific logic. */
    private val keyboardExtractor = KeyboardNodeExtractor()

    /** Holds the list of all nodes. */
    private var allNodes: List<Node> = emptyList()

    /** Holds the list of actionable nodes. */
    private var actionableNodes: List<Node> = emptyList()

    /** SharedFlow for emitting updates to the list of actionable nodes. */
    private val actionableNodesFlow =
        MutableSharedFlow<List<Node>>(replay = 1, extraBufferCapacity = 1)

    /** SharedFlow for emitting updates to the list of keyboard nodes. */
    private val keyboardNodesFlow =
        MutableSharedFlow<List<Node>>(replay = 1, extraBufferCapacity = 1)

    /**
     * Provides a Flow to observe changes in the list of actionable nodes.
     *
     * @return A Flow emitting lists of Node objects whenever there's an update.
     */
    fun getActionableNodesFlow(): Flow<List<Node>> = actionableNodesFlow.asSharedFlow()

    /**
     * Provides a Flow to observe changes in the list of keyboard nodes.
     *
     * @return A Flow emitting lists of Node objects whenever there's an update.
     */
    fun getKeyboardNodesFlow(): Flow<List<Node>> = keyboardNodesFlow.asSharedFlow()

    /**
     * Initiates the process of finding and updating the list of nodes.
     * It flattens the accessibility tree starting from the rootNode, filters out nodes not on the screen,
     * and emits an update if the actionable nodes have changed. Includes deep content examination for empty nodes
     * and filtering to identify the smallest nested nodes for all UI elements.
     *
     * @param activeWindowRootNode The root node of the active window.
     * @param windows The list of windows.
     * @param context The current context, used to get screen dimensions for filtering nodes.
     * @param coroutineScope The CoroutineScope in which to perform the node examination.
     */
    fun examineAccessibilityTree(
        activeWindowRootNode: AccessibilityNodeInfo?,
        windows: List<AccessibilityWindowInfo>,
        context: Context,
        coroutineScope: CoroutineScope
    ) {
        // Use keyboard extractor to determine if keyboard is visible and get appropriate root node
        val isKeyboardVisible = keyboardExtractor.isKeyboardVisible(windows)

        // Determine which root node to use based on whether a keyboard is visible
        val rootNode = if (isKeyboardVisible) {
            keyboardExtractor.getKeyboardRootNode(windows)
        } else {
            activeWindowRootNode
        }

        try {
            rootNode?.let { rootNode ->
                coroutineScope.launch(Dispatchers.Default) {
                    val result = withTimeoutOrNull(TREE_PROCESSING_TIMEOUT_MS) {
                        processAccessibilityTree(rootNode, context, isKeyboardVisible)
                    }

                    if (result == null) {
                        Log.w(
                            TAG,
                            "Accessibility tree processing timed out after ${TREE_PROCESSING_TIMEOUT_MS}ms"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error examining accessibility tree", e)
        }
    }

    private suspend fun processAccessibilityTree(
        rootNode: AccessibilityNodeInfo,
        context: Context,
        isKeyboardVisible: Boolean
    ) {
        // Flatten the accessibility tree to get all nodes
        val newNodeInfos = flattenTree(rootNode)

        // Early termination for oversized trees
        if (newNodeInfos.size > MAX_NODES_THRESHOLD) {
            Log.w(TAG, "Tree too large (${newNodeInfos.size} nodes), skipping detailed processing")
            return
        }

        // Enhanced node examination for all nodes
        allNodes = newNodeInfos.map { nodeInfo ->
            examineNodeContent(nodeInfo)
        }

        // Enhanced node examination for actionable nodes
        // Include nodes that are clickable, long-clickable, focusable, or have ACTION_CLICK
        val newActionableNodes = newNodeInfos
            .filter { nodeInfo ->
                nodeInfo.isClickable ||
                        nodeInfo.isLongClickable ||
                        nodeInfo.isFocusable ||
                        nodeInfo.actionList?.any { action ->
                            action.id == AccessibilityNodeInfo.ACTION_CLICK
                        } == true
            }
            .map { examineNodeContent(it) }

        // Get screen dimensions for filtering nodes
        val width = ScreenUtils.getWidth(context)
        val height = ScreenUtils.getHeight(context)

        // Filter out nodes that are not on the screen or have invalid dimensions
        val filteredNewActionableNodes = newActionableNodes.filter { node ->
            node.getLeft() >= 0 && node.getTop() >= 0 &&
                    node.getLeft() <= width && node.getTop() <= height &&
                    node.getWidth() > 0 && node.getHeight() > 0
        }

        // Apply the smallest nested nodes filter to all nodes
        val smallestNestedNodes = filterSmallestNestedNodesOptimized(filteredNewActionableNodes)
        Log.d(
            TAG,
            "Filtered from ${filteredNewActionableNodes.size} to ${smallestNestedNodes.size} nodes"
        )

        // Only update if the nodes have changed
        if (actionableNodes != smallestNestedNodes) {
            if (isKeyboardVisible) {
                updateKeyboardNodes(smallestNestedNodes)
            } else {
                updateActionableNodes(smallestNestedNodes)
            }
        }
    }

    /**
     * Optimized version that filters a list of nodes, keeping only the smallest nodes in nested hierarchies.
     * Uses spatial indexing to reduce O(n²) complexity for large node lists.
     *
     * @param nodes The list of Node objects to filter.
     * @return A new list containing only the smallest nodes in each nested group.
     */
    private fun filterSmallestNestedNodesOptimized(nodes: List<Node>): List<Node> {
        // If there's 0 or 1 node, no nesting is possible, return the original list
        if (nodes.size < 2) {
            return nodes.toList()
        }

        // For smaller lists, use the original algorithm
        if (nodes.size < 50) {
            return filterSmallestNestedNodes(nodes)
        }

        // For larger lists, use optimized approach
        val nodesToDiscard = mutableSetOf<Node>()

        // Sort nodes by area (smaller first) to optimize containment checks
        val sortedNodes = nodes.sortedBy { node ->
            try {
                val bounds = node.getBounds()
                if (bounds.isEmpty == false) bounds.width() * bounds.height() else Int.MAX_VALUE
            } catch (e: Exception) {
                Int.MAX_VALUE
            }
        }

        // Only check nodes that could potentially contain others
        for (i in sortedNodes.indices) {
            if (nodesToDiscard.contains(sortedNodes[i])) continue

            val nodeA = sortedNodes[i]
            val boundsA = try {
                nodeA.getBounds()
            } catch (e: Exception) {
                continue
            }

            if (boundsA.isEmpty != false) continue

            // Only check against larger nodes (those that come after in sorted order)
            for (j in (i + 1) until sortedNodes.size) {
                val nodeB = sortedNodes[j]
                val boundsB = try {
                    nodeB.getBounds()
                } catch (e: Exception) {
                    continue
                }

                if (boundsB.isEmpty != false) continue

                // If nodeB contains nodeA, mark nodeB for removal (keep the smaller nodeA)
                if (boundsB.contains(boundsA) && boundsB != boundsA) {
                    nodesToDiscard.add(nodeB)
                }
            }
        }

        return nodes.filterNot { nodesToDiscard.contains(it) }
    }

    /**
     * Legacy method for smaller node lists - maintains O(n²) complexity but simpler logic.
     */
    private fun filterSmallestNestedNodes(nodes: List<Node>): List<Node> {
        val nodesToDiscard = mutableSetOf<Node>()

        for (i in nodes.indices) {
            for (j in nodes.indices) {
                if (i == j) continue

                val nodeA = nodes[i]
                val nodeB = nodes[j]

                val boundsA = try {
                    nodeA.getBounds()
                } catch (e: Exception) {
                    null
                }
                val boundsB = try {
                    nodeB.getBounds()
                } catch (e: Exception) {
                    null
                }

                if (boundsA?.isEmpty != false || boundsB?.isEmpty != false) continue

                if (boundsA.contains(boundsB) && boundsA != boundsB) {
                    nodesToDiscard.add(nodeA)
                }
            }
        }

        return nodes.filterNot { nodesToDiscard.contains(it) }
    }

    /**
     * Updates the keyboard nodes and emits them to the keyboardNodesFlow.
     *
     * @param nodes The list of nodes to update the keyboard nodes with.
     */
    private suspend fun updateKeyboardNodes(nodes: List<Node>) {
        keyboardNodesFlow.emit(nodes)
    }

    /**
     * Updates the actionable nodes and emits them to the actionableNodesFlow.
     *
     * @param nodes The list of nodes to update the actionable nodes with.
     */
    private suspend fun updateActionableNodes(nodes: List<Node>) {
        actionableNodesFlow.emit(nodes)
    }

    /**
     * Examines a node's content thoroughly, looking into child nodes if necessary.
     * If the node has empty content description, attempts to build content from its children.
     *
     * @param node The AccessibilityNodeInfo to examine.
     * @return A Node object with populated content description where possible.
     */
    private suspend fun examineNodeContent(node: AccessibilityNodeInfo): Node {
        try {
            val baseNode = Node.fromAccessibilityNodeInfo(node)

            // If content description is empty, try to build it from child nodes
            if (baseNode.getContentDescription().isEmpty()) {
                val contentFromChildren = buildContentFromChildren(node)
                if (contentFromChildren.isNotEmpty()) {
                    val newNode = baseNode.apply { setContentDescription(contentFromChildren) }
                    return newNode
                }
            }

            return baseNode
        } catch (e: Exception) {
            Log.e(TAG, "Error examining node content", e)
            return Node.fromAccessibilityNodeInfo(node)
        }
    }

    /**
     * Recursively builds content description from child nodes.
     * Examines both content descriptions and text of child nodes.
     *
     * @param node The AccessibilityNodeInfo whose children to examine.
     * @return A string containing combined content from child nodes.
     */
    private suspend fun buildContentFromChildren(node: AccessibilityNodeInfo): String =
        withContext(Dispatchers.Default) {
            val contentParts = mutableListOf<String>()

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { childNode ->
                    // Check child's content description
                    childNode.contentDescription?.toString()?.let {
                        if (it.isNotBlank()) {
                            contentParts.add(it)
                        }
                    }

                    // Check child's text
                    childNode.text?.toString()?.let {
                        if (it.isNotBlank()) {
                            contentParts.add(it)
                        }
                    }

                    // If child also has no content, go deeper
                    if (contentParts.isEmpty()) {
                        val childContent = buildContentFromChildren(childNode)
                        if (childContent.isNotEmpty()) {
                            contentParts.add(childContent)
                        }
                    }
                }
            }

            contentParts.joinToString(" ")
        }

    /**
     * Flattens the given tree of AccessibilityNodeInfo objects into a list.
     * This method explores the tree breadth-first to collect all nodes.
     *
     * @param rootNode The root node of the tree to start flattening from.
     * @return A list of all AccessibilityNodeInfo objects in the tree.
     */
    private suspend fun flattenTree(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
        withContext(Dispatchers.Default) {
            val nodes: MutableList<AccessibilityNodeInfo> = ArrayList()
            val q: Queue<AccessibilityNodeInfo> = LinkedList()
            q.add(rootNode)

            while (q.isNotEmpty()) {
                val node = q.poll()
                node?.let { accessibilityNodeInfo ->
                    nodes.add(accessibilityNodeInfo)
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { q.add(it) }
                    }
                }
            }
            nodes
        }

    /**
     * Finds the node that can perform the given action at the given point.
     *
     * @param point The point to find the node at.
     * @param actionType The action to find the node for.
     * @return The node that can perform the given action at the given point, or null if no such node exists.
     */
    fun findNodeForAction(point: PointF, actionType: Node.ActionType): Node? {
        return allNodes.find { it.containsPoint(point) && it.isActionable(actionType) }
    }

    /**
     * Checks if a node can perform any edit actions at the given point.
     *
     * @param point The point to check for edit actions.
     * @return True if a node can perform any edit actions at the given point, false otherwise.
     */
    fun canPerformEditActions(point: PointF): Boolean {
        return findNodeForAction(point, Node.ActionType.CUT) != null ||
                findNodeForAction(point, Node.ActionType.COPY) != null ||
                findNodeForAction(point, Node.ActionType.PASTE) != null
    }

    /**
     * Finds the closest node to a given point on the screen.
     *
     * @param point The point for which to find the closest node.
     * @return The closest node's center point. Returns the original point if no close node is found.
     */
    fun getClosestNodeToPoint(point: PointF): PointF {
        val maxDistance = 200f
        return actionableNodes
            .map { PointF(it.getMidX().toFloat(), it.getMidY().toFloat()) }
            .minByOrNull { distanceBetweenPoints(point, it) }
            ?.takeIf { distanceBetweenPoints(point, it) < maxDistance }
            ?: point
    }

    /**
     * Calculates the Euclidean distance between two points.
     *
     * @param point1 The first point.
     * @param point2 The second point.
     * @return The distance between point1 and point2.
     */
    private fun distanceBetweenPoints(point1: PointF, point2: PointF): Float {
        val xDiff = point1.x - point2.x
        val yDiff = point1.y - point2.y
        return sqrt((xDiff * xDiff + yDiff * yDiff))
    }
}