package com.enaboapps.switchify.service.techniques.nodes

import android.content.Context
import android.graphics.PointF
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.keyboard.KeyboardNodeExtractor
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal data class NodeScanSnapshot(val nodes: List<Node>, val source: String?, val revision: Long)

object NodeExaminer {
    private val keyboardExtractor = KeyboardNodeExtractor()
    private var allNodes: List<Node> = emptyList()
    private var actionableNodes: List<Node> = emptyList()
    private var snapshotRevision = 0L
    private val actionableNodesFlow = MutableStateFlow(NodeScanSnapshot(emptyList(), null, snapshotRevision))
    private val _keyboardNodesState = MutableStateFlow(KeyboardNodesState())
    val keyboardNodesState: StateFlow<KeyboardNodesState> = _keyboardNodesState.asStateFlow()
    private val failures = NodeProcessingCooldown()
    private var applicationSource: String? = null

    internal fun getActionableNodesFlow(): Flow<NodeScanSnapshot> = actionableNodesFlow.asStateFlow()

    internal fun clear() {
        allNodes = emptyList()
        actionableNodes = emptyList()
        actionableNodesFlow.value = NodeScanSnapshot(emptyList(), null, ++snapshotRevision)
        _keyboardNodesState.value = KeyboardNodesState()
        failures.reset()
        applicationSource = null
    }

    suspend fun examineAccessibilityTree(
        activeWindowRootNode: AccessibilityNodeInfo?,
        windows: List<AccessibilityWindowInfo>,
        context: Context,
        isCurrent: () -> Boolean = { true }
    ) {
        val coroutineContext = currentCoroutineContext()
        val budget = NodeProcessingBudget(checkCancellation = {
            coroutineContext.ensureActive()
            if (!isCurrent()) throw CancellationException("Obsolete node examination")
        })
        val keyboardState = KeyboardManager.keyboardState.value
        val examineKeyboard = shouldExamineKeyboardRoot(keyboardState.isVisible, keyboardState.isEscaped)
        val root = (if (examineKeyboard) {
            keyboardExtractor.getKeyboardRootNode(windows)
        } else if (shouldExamineApplicationRoot(keyboardState.isVisible, keyboardState.isEscaped)) {
            findApplicationRootNode(windows) ?: activeWindowRootNode
        } else {
            activeWindowRootNode
        }) ?: return
        val displayId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) root.window?.displayId ?: 0 else 0
        val source = "${root.packageName}:$displayId:${root.windowId}"
        withContext(Dispatchers.Main.immediate) {
            if (!isCurrent()) return@withContext
            if (!examineKeyboard && applicationSource != source) {
                allNodes = emptyList()
                actionableNodes = emptyList()
                actionableNodesFlow.value = NodeScanSnapshot(emptyList(), source, ++snapshotRevision)
                applicationSource = source
            }
            if (!keyboardState.isVisible) _keyboardNodesState.value = KeyboardNodesState()
        }
        if (!withContext(Dispatchers.Main.immediate) { isCurrent() && failures.canProcess(source) }) return
        try {
            budget.check()
            val flattened = traverseNodes(root, budget, { it.childCount }, { node, index -> node.getChild(index) })
            val width = ScreenUtils.getWidth(context)
            val height = ScreenUtils.getHeight(context)
            val mapped = flattened.map {
                budget.check()
                Node.fromAccessibilityNodeInfo(it.node, it.path, budget.text(it.node.contentDescription))
            }
            val candidates = mapped.filterIndexed { index, node ->
                budget.check()
                val included = node.getLeft() >= 0 && node.getTop() >= 0 &&
                    node.getLeft() <= width && node.getTop() <= height &&
                    node.getWidth() > 0 && node.getHeight() > 0 && node.isCurrentlyScannable()
                if (included && node.getContentDescription().isEmpty()) {
                    node.setContentDescription(descendantLabel(index, flattened, budget))
                }
                included
            }
            val selected = filterContainedNodes(candidates, budget::check) {
                NodeRectangle(it.getLeft(), it.getTop(), it.getWidth(), it.getHeight())
            }
            budget.check()
            withContext(Dispatchers.Main.immediate) {
                if (!isCurrent()) return@withContext
                allNodes = mapped
                if (examineKeyboard) {
                    _keyboardNodesState.value = KeyboardNodesState(selected, keyboardState.keyboardBounds, source, ++snapshotRevision)
                } else {
                    actionableNodes = selected
                    actionableNodesFlow.value = NodeScanSnapshot(selected, source, ++snapshotRevision)
                }
                failures.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: NodeTraversalLimitException) {
            withContext(Dispatchers.Main.immediate) {
                if (isCurrent()) {
                    failures.failure(source)
                    Logger.log(LogEvent.NodeTreeTooLarge, data = mapOf("result" to "skipped", "reason" to "processing_budget"))
                }
            }
        } catch (error: Exception) {
            withContext(Dispatchers.Main.immediate) {
                if (isCurrent()) {
                    failures.failure(source)
                    Logger.log(LogEvent.NodeExaminerFailed, throwable = error)
                }
            }
        }
    }

    internal fun isExpectedNodeExaminerCancellation(error: Throwable): Boolean =
        error is CancellationException

    internal fun shouldExamineKeyboardRoot(isKeyboardVisible: Boolean, isEscapedFromKeyboard: Boolean): Boolean =
        isKeyboardVisible && !isEscapedFromKeyboard

    internal fun shouldExamineApplicationRoot(isKeyboardVisible: Boolean, isEscapedFromKeyboard: Boolean): Boolean =
        isKeyboardVisible && isEscapedFromKeyboard

    private fun findApplicationRootNode(windows: List<AccessibilityWindowInfo>): AccessibilityNodeInfo? =
        windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedWith(compareByDescending<AccessibilityWindowInfo> { it.isActive }
                .thenByDescending { it.isFocused }.thenByDescending { it.layer })
            .mapNotNull { it.root }
            .firstOrNull()

    private fun descendantLabel(
        index: Int,
        nodes: List<TraversedNode<AccessibilityNodeInfo>>,
        budget: NodeProcessingBudget,
        depth: Int = 0
    ): String {
        if (depth >= 3) return ""
        val content = StringBuilder()
        for (child in nodes[index].children) {
            budget.check()
            if (content.length >= 1024) break
            val node = nodes[child].node
            for (value in listOf(node.contentDescription, node.text)) {
                val text = budget.text(value, (1024 - content.length - if (content.isEmpty()) 0 else 1).coerceAtLeast(0))
                if (text.isNotBlank()) {
                    if (content.isNotEmpty()) content.append(' ')
                    content.append(text)
                }
            }
            if (content.isEmpty()) content.append(descendantLabel(child, nodes, budget, depth + 1))
        }
        return content.toString()
    }

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

    internal fun findActionTarget(point: PointF, context: Context): NodeActionTarget? {
        val candidates = allNodes.mapIndexedNotNull { index, node ->
            if (!node.containsPoint(point)) return@mapIndexedNotNull null
            val actions = NodeActionPolicy.resolve(
                actions = node.reportedActions(),
                standardLabel = { actionId ->
                    AndroidNodeActionLabels.standardLabel(context, actionId)
                },
                isExcluded = AndroidNodeActionLabels::isExcluded
            )
            if (actions.isEmpty()) return@mapIndexedNotNull null

            val locator = node.actionLocator(point) ?: return@mapIndexedNotNull null
            val target = NodeActionTarget(locator, actions)
            NodeActionCandidate(
                target = target,
                left = node.getLeft(),
                top = node.getTop(),
                width = node.getWidth(),
                height = node.getHeight(),
                traversalOrder = index
            )
        }

        return NodeActionTargetSelector.selectSmallest(point.x, point.y, candidates)
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
