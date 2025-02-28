package com.enaboapps.switchify.service.methods.nodes

import android.content.Context
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.utils.ScreenUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.Queue
import kotlin.math.sqrt

/**
 * NodeExaminer is responsible for examining accessibility nodes within an application's UI.
 * It provides methods to find, filter, and analyze nodes, as well as to observe changes in the node structure.
 * This implementation includes deep content description analysis for nodes with empty content.
 */
object NodeExaminer {
    private const val TAG = "NodeExaminer"

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
     * and emits an update if the actionable nodes have changed. Includes deep content examination for empty nodes.
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
        var rootNode: AccessibilityNodeInfo? = null
        val inputMethodWindow = windows.firstOrNull { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        val isKeyboardVisible = inputMethodWindow != null
        rootNode = if (isKeyboardVisible) {
            inputMethodWindow.root
        } else {
            activeWindowRootNode
        }
        try {
            rootNode?.let { rootNode ->
                coroutineScope.launch(Dispatchers.Default) {
                    val newNodeInfos = flattenTree(rootNode)

                    // Enhanced node examination for all nodes
                    allNodes = newNodeInfos.map { nodeInfo ->
                        examineNodeContent(nodeInfo)
                    }

                    // Enhanced node examination for actionable nodes
                    val newActionableNodes = newNodeInfos
                        .filter { it.isClickable || it.isLongClickable }
                        .map { examineNodeContent(it) }

                    val width = ScreenUtils.getWidth(context)
                    val height = ScreenUtils.getHeight(context)

                    val filteredNewActionableNodes = newActionableNodes.filter {
                        it.getLeft() >= 0 && it.getTop() >= 0 &&
                                it.getLeft() <= width && it.getTop() <= height &&
                                it.getWidth() > 0 && it.getHeight() > 0
                    }

                    if (actionableNodes != filteredNewActionableNodes) {
                        if (isKeyboardVisible) {
                            updateKeyboardNodes(filteredNewActionableNodes)
                        } else {
                            updateActionableNodes(filteredNewActionableNodes)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                        contentParts.add(it)
                    }

                    // Check child's text
                    childNode.text?.toString()?.let {
                        contentParts.add(it)
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
     * Calculates the distance between two points.
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