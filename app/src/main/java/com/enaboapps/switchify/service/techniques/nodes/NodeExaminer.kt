package com.enaboapps.switchify.service.techniques.nodes

import android.content.Context
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.keyboard.KeyboardNodeExtractor
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * NodeExaminer is responsible for examining accessibility nodes within an application's UI.
 * It provides methods to find, filter, and analyze nodes, as well as to observe changes in the node structure.
 * This implementation includes deep content description analysis for nodes with empty content
 * and filtering to identify the smallest nested nodes for all UI elements.
 */
object NodeExaminer {
    private const val TAG = "NodeExaminer"
    private const val TREE_PROCESSING_TIMEOUT_MS = 10_000L
    private const val MAX_NODES_THRESHOLD = 1000
    private const val MAX_CHILD_DEPTH = 3
    private const val CIRCUIT_BREAKER_THRESHOLD = 5
    private const val CIRCUIT_BREAKER_COOLDOWN_MS = 10_000L

    /** Circuit breaker state */
    private var consecutiveFailures = 0
    private var cooldownUntil = 0L
    private var treeTooLargeLogged = false

    /** Keyboard node extractor for handling keyboard-specific logic. */
    private val keyboardExtractor = KeyboardNodeExtractor()

    /** Holds the list of all nodes. */
    private var allNodes: List<Node> = emptyList()

    /** Holds the list of actionable nodes. */
    private var actionableNodes: List<Node> = emptyList()

    /** SharedFlow for emitting updates to the list of actionable nodes. */
    private val actionableNodesFlow =
        MutableSharedFlow<List<Node>>(replay = 1, extraBufferCapacity = 1)

    /**
     * StateFlow holding the latest keyboard nodes and the IME bounds they were
     * captured against. Conflated by equality, so identical re-emissions are
     * no-ops. Late subscribers always see the current value via `.value`.
     */
    private val _keyboardNodesState = MutableStateFlow(KeyboardNodesState())
    val keyboardNodesState: StateFlow<KeyboardNodesState> = _keyboardNodesState.asStateFlow()

    /**
     * Provides a Flow to observe changes in the list of actionable nodes.
     *
     * @return A Flow emitting lists of Node objects whenever there's an update.
     */
    fun getActionableNodesFlow(): Flow<List<Node>> = actionableNodesFlow.asSharedFlow()

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
    suspend fun examineAccessibilityTree(
        activeWindowRootNode: AccessibilityNodeInfo?,
        windows: List<AccessibilityWindowInfo>,
        context: Context
    ) {
        // Circuit breaker: skip processing if in cooldown
        if (System.currentTimeMillis() < cooldownUntil) {
            return
        }

        // Read visibility from KeyboardManager so this stays consistent with
        // ActiveAccessTechnique's scanner routing. NodeUpdateCoordinator pushes
        // KeyboardBridge state before calling examineAccessibilityTree, so the
        // StateFlow value reflects this event's windows.
        val isKeyboardVisible = KeyboardManager.keyboardState.value.isVisible

        val rootNode = if (isKeyboardVisible) {
            keyboardExtractor.getKeyboardRootNode(windows)
        } else {
            activeWindowRootNode
        }

        try {
            rootNode?.let { rootNode ->
                val startTime = System.currentTimeMillis()
                val result = withTimeoutOrNull(TREE_PROCESSING_TIMEOUT_MS) {
                    processAccessibilityTree(rootNode, context, isKeyboardVisible)
                }
                val elapsed = System.currentTimeMillis() - startTime

                if (result == null) {
                    Log.w(
                        TAG,
                        "Accessibility tree processing timed out after ${elapsed}ms (limit: ${TREE_PROCESSING_TIMEOUT_MS}ms)"
                    )
                    Logger.log(
                        LogEvent.NodeTreeProcessingTimeout,
                        data = mapOf(
                            "result" to "timeout",
                            "timeout_ms" to TREE_PROCESSING_TIMEOUT_MS,
                            "elapsed_ms" to elapsed,
                            "keyboard_visible" to isKeyboardVisible,
                            "app_package" to (rootNode.packageName?.toString() ?: "unknown")
                        )
                    )
                    recordFailure(rootNode.packageName?.toString())
                } else {
                    consecutiveFailures = 0
                    treeTooLargeLogged = false
                    if (elapsed > TREE_PROCESSING_TIMEOUT_MS / 2) {
                        Log.d(TAG, "Tree processing took ${elapsed}ms (>50% of timeout)")
                    }
                }
            }
        } catch (e: Exception) {
            val packageName = rootNode?.packageName?.toString()
            Log.e(TAG, "Error examining accessibility tree (app=$packageName)", e)
            Logger.log(
                LogEvent.NodeExaminerFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "examine_accessibility_tree_exception",
                    "keyboard_visible" to isKeyboardVisible,
                    "app_package" to (packageName ?: "unknown")
                ),
                throwable = e
            )
            recordFailure(packageName)
        }
    }

    private fun recordFailure(packageName: String?) {
        consecutiveFailures++
        if (consecutiveFailures >= CIRCUIT_BREAKER_THRESHOLD) {
            cooldownUntil = System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN_MS
            Log.w(
                TAG,
                "Circuit breaker tripped after $consecutiveFailures consecutive failures (app=$packageName), cooling down for ${CIRCUIT_BREAKER_COOLDOWN_MS}ms"
            )
            Logger.log(
                LogEvent.NodeExaminerCircuitBreakerTripped,
                data = mapOf(
                    "consecutive_failures" to consecutiveFailures,
                    "cooldown_ms" to CIRCUIT_BREAKER_COOLDOWN_MS,
                    "app_package" to (packageName ?: "unknown")
                )
            )
            consecutiveFailures = 0
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
            if (!treeTooLargeLogged) {
                Log.w(TAG, "Tree too large (${newNodeInfos.size} nodes), skipping detailed processing")
                Logger.log(
                    LogEvent.NodeTreeTooLarge,
                    data = mapOf(
                        "result" to "skipped",
                        "node_count" to newNodeInfos.size,
                        "max_threshold" to MAX_NODES_THRESHOLD,
                        "keyboard_visible" to isKeyboardVisible,
                        "app_package" to (rootNode.packageName?.toString() ?: "unknown")
                    )
                )
                treeTooLargeLogged = true
            }
            recordFailure(rootNode.packageName?.toString())
            return
        }

        // Lightweight mapping for all nodes (no deep content examination)
        allNodes = newNodeInfos.map { nodeInfo ->
            Node.fromAccessibilityNodeInfo(nodeInfo)
        }

        // Deep content examination only for actionable nodes
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
            val w = node.getWidth()
            val h = node.getHeight()
            if (w > 0 && h > 0) w * h else Int.MAX_VALUE
        }

        // Only check nodes that could potentially contain others
        for (i in sortedNodes.indices) {
            if (nodesToDiscard.contains(sortedNodes[i])) continue

            val nodeA = sortedNodes[i]
            if (nodeA.getWidth() <= 0 || nodeA.getHeight() <= 0) continue

            // Only check against larger nodes (those that come after in sorted order)
            for (j in (i + 1) until sortedNodes.size) {
                val nodeB = sortedNodes[j]
                if (nodeB.getWidth() <= 0 || nodeB.getHeight() <= 0) continue

                // If nodeB contains nodeA, mark nodeB for removal (keep the smaller nodeA)
                if (nodeContains(nodeB, nodeA) && !sameBounds(nodeA, nodeB)) {
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

                if (nodeA.getWidth() <= 0 || nodeA.getHeight() <= 0) continue
                if (nodeB.getWidth() <= 0 || nodeB.getHeight() <= 0) continue

                if (nodeContains(nodeA, nodeB) && !sameBounds(nodeA, nodeB)) {
                    nodesToDiscard.add(nodeA)
                }
            }
        }

        return nodes.filterNot { nodesToDiscard.contains(it) }
    }

    /** Returns true if [outer] fully contains [inner] using raw int bounds. */
    private fun nodeContains(outer: Node, inner: Node): Boolean {
        return outer.getLeft() <= inner.getLeft() &&
                outer.getTop() <= inner.getTop() &&
                outer.getLeft() + outer.getWidth() >= inner.getLeft() + inner.getWidth() &&
                outer.getTop() + outer.getHeight() >= inner.getTop() + inner.getHeight()
    }

    /** Returns true if [a] and [b] have identical bounds. */
    private fun sameBounds(a: Node, b: Node): Boolean {
        return a.getLeft() == b.getLeft() &&
                a.getTop() == b.getTop() &&
                a.getWidth() == b.getWidth() &&
                a.getHeight() == b.getHeight()
    }

    /**
     * Updates the keyboard nodes state. Bounds are pulled from KeyboardManager,
     * which NodeUpdateCoordinator refreshes via KeyboardBridge before this
     * function runs, so the snapshot reflects the current IME.
     *
     * @param nodes The list of nodes to update the keyboard nodes with.
     */
    private fun updateKeyboardNodes(nodes: List<Node>) {
        _keyboardNodesState.value = KeyboardNodesState(
            nodes = nodes,
            keyboardBounds = KeyboardManager.keyboardState.value.keyboardBounds
        )
    }

    /**
     * Updates the actionable nodes and emits them to the actionableNodesFlow.
     *
     * @param nodes The list of nodes to update the actionable nodes with.
     */
    private suspend fun updateActionableNodes(nodes: List<Node>) {
        actionableNodes = nodes
        actionableNodesFlow.emit(nodes)
    }

    /**
     * Examines a node's content thoroughly, looking into child nodes if necessary.
     * If the node has empty content description, attempts to build content from its children.
     *
     * @param node The AccessibilityNodeInfo to examine.
     * @return A Node object with populated content description where possible.
     */
    private fun examineNodeContent(node: AccessibilityNodeInfo): Node {
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
            Logger.log(
                LogEvent.NodeExaminerFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "examine_node_content_exception"
                ),
                throwable = e
            )
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
    private fun buildContentFromChildren(
        node: AccessibilityNodeInfo,
        depth: Int = 0
    ): String {
        if (depth >= MAX_CHILD_DEPTH) return ""

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

                // If child also has no content, go deeper (with depth limit)
                if (contentParts.isEmpty()) {
                    val childContent = buildContentFromChildren(childNode, depth + 1)
                    if (childContent.isNotEmpty()) {
                        contentParts.add(childContent)
                    }
                }
            }
        }

        return contentParts.joinToString(" ")
    }

    /**
     * Flattens the given tree of AccessibilityNodeInfo objects into a list.
     * This method explores the tree breadth-first to collect all nodes.
     *
     * @param rootNode The root node of the tree to start flattening from.
     * @return A list of all AccessibilityNodeInfo objects in the tree.
     */
    private fun flattenTree(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = ArrayList<AccessibilityNodeInfo>(64)
        val queue = ArrayDeque<AccessibilityNodeInfo>(64)
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            nodes.add(node)
            // Early exit if tree is too large
            if (nodes.size > MAX_NODES_THRESHOLD) {
                return nodes
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return nodes
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
        val maxDistanceSquared = 200f * 200f
        var bestNode: Node? = null
        var bestDistSq = Float.MAX_VALUE

        for (node in actionableNodes) {
            val dx = node.getMidX().toFloat() - point.x
            val dy = node.getMidY().toFloat() - point.y
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestNode = node
            }
        }

        return if (bestNode != null && bestDistSq < maxDistanceSquared) {
            PointF(bestNode.getMidX().toFloat(), bestNode.getMidY().toFloat())
        } else {
            point
        }
    }
}
