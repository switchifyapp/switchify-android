package com.enaboapps.switchify.service.techniques.nodes

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ReportedNodeAction(val id: Int, val label: String?)

internal data class NodeActionDescriptor(val id: Int, val label: String)

internal data class NodeActionBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun centerDistanceSquared(other: NodeActionBounds): Long {
        val dx = (left.toLong() + right) - (other.left.toLong() + other.right)
        val dy = (top.toLong() + bottom) - (other.top.toLong() + other.bottom)
        return dx * dx + dy * dy
    }
}

internal data class NodeActionIdentity(
    val packageName: String?,
    val windowId: Int,
    val childPath: List<Int>,
    val bounds: NodeActionBounds,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val uniqueId: String?
)

internal data class NodeActionLocator(
    val identity: NodeActionIdentity,
    val selectionX: Float,
    val selectionY: Float,
    val reportedActionIds: Set<Int>
)

internal data class NodeActionTarget(
    val locator: NodeActionLocator,
    val actions: List<NodeActionDescriptor>,
    val excludedActionIds: Set<Int> = emptySet()
)

internal class ResolvedNodeActionTarget(
    val actions: List<NodeActionDescriptor>,
    private val actionPerformer: (Int) -> Boolean
) {
    fun perform(actionId: Int): Boolean = actionPerformer(actionId)
}

internal data class NodeActionCandidate<T>(
    val target: T,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val traversalOrder: Int = 0
) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= left + width && y >= top && y <= top + height
}

internal object NodeActionTargetSelector {
    fun <T> selectSmallest(
        x: Float,
        y: Float,
        candidates: List<NodeActionCandidate<T>>
    ): T? = candidates
        .asSequence()
        .filter { it.width > 0 && it.height > 0 && it.contains(x, y) }
        .minWithOrNull(
            compareBy<NodeActionCandidate<T>> { it.width.toLong() * it.height.toLong() }
                .thenByDescending { it.traversalOrder }
        )
        ?.target
}

internal object NodeActionLocatorMatcher {
    fun <T> find(
        locator: NodeActionLocator,
        candidates: List<Pair<NodeActionIdentity, T>>
    ): T? {
        val sourceCandidates = candidates.filter { (identity, _) ->
            identity.packageName == locator.identity.packageName &&
                identity.windowId == locator.identity.windowId
        }
        if (sourceCandidates.isEmpty()) return null

        locator.identity.uniqueId?.takeIf { it.isNotBlank() }?.let { uniqueId ->
            sourceCandidates.firstOrNull { it.first.uniqueId == uniqueId }?.let { return it.second }
        }

        locator.identity.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { viewId ->
            chooseByBounds(
                locator,
                sourceCandidates.filter { (identity, _) ->
                    identity.viewIdResourceName == viewId && classesMatch(locator, identity)
                }
            )?.let { return it }
        }

        sourceCandidates.firstOrNull { (identity, _) ->
            identity.childPath == locator.identity.childPath &&
                classesMatch(locator, identity) &&
                identity.bounds.contains(locator.selectionX, locator.selectionY)
        }?.let { return it.second }

        return chooseByBounds(
            locator,
            sourceCandidates.filter { (identity, _) ->
                classesMatch(locator, identity) &&
                    identity.bounds.contains(locator.selectionX, locator.selectionY) &&
                    semanticIdentityMatches(locator.identity, identity)
            }
        )
    }

    private fun classesMatch(locator: NodeActionLocator, candidate: NodeActionIdentity): Boolean =
        locator.identity.className == null || locator.identity.className == candidate.className

    private fun semanticIdentityMatches(
        locator: NodeActionIdentity,
        candidate: NodeActionIdentity
    ): Boolean {
        val textMatches = locator.text?.takeIf { it.isNotBlank() }?.let { it == candidate.text } == true
        val descriptionMatches = locator.contentDescription
            ?.takeIf { it.isNotBlank() }
            ?.let { it == candidate.contentDescription } == true
        return textMatches || descriptionMatches
    }

    private fun <T> chooseByBounds(
        locator: NodeActionLocator,
        candidates: List<Pair<NodeActionIdentity, T>>
    ): T? = candidates.minByOrNull { (identity, _) ->
        identity.bounds.centerDistanceSquared(locator.identity.bounds)
    }?.second
}

internal object NodeActionPolicy {
    fun resolve(
        actions: List<ReportedNodeAction>,
        standardLabel: (Int) -> String?,
        isExcluded: (Int) -> Boolean
    ): List<NodeActionDescriptor> {
        val seen = mutableSetOf<Int>()
        return actions.mapNotNull { action ->
            if (!seen.add(action.id) || isExcluded(action.id)) return@mapNotNull null
            val label = action.label?.trim()?.takeIf { it.isNotEmpty() }
                ?: standardLabel(action.id)
                ?: return@mapNotNull null
            NodeActionDescriptor(action.id, label)
        }
    }
}

internal sealed interface NodeActionResolution {
    data object SourceMissing : NodeActionResolution
    data object TargetMissing : NodeActionResolution
    data class Resolved(val target: ResolvedNodeActionTarget) : NodeActionResolution
}

internal fun interface NodeActionResolver {
    suspend fun resolve(locator: NodeActionLocator, excludedActionIds: Set<Int>): NodeActionResolution
}

internal class AndroidNodeActionResolver(
    private val service: AccessibilityService,
    private val context: Context
) : NodeActionResolver {
    override suspend fun resolve(
        locator: NodeActionLocator,
        excludedActionIds: Set<Int>
    ): NodeActionResolution {
        val coroutineContext = currentCoroutineContext()
        val budget = NodeProcessingBudget(checkCancellation = { coroutineContext.ensureActive() })
        val window = service.windows.firstOrNull { candidate ->
            candidate.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                candidate.id == locator.identity.windowId &&
                candidate.root?.packageName?.toString() == locator.identity.packageName
        } ?: return NodeActionResolution.SourceMissing
        val root = window.root ?: return NodeActionResolution.SourceMissing
        val candidates = traverseNodes(root, budget, { it.childCount }, { node, index -> node.getChild(index) })
            .map { (node, path) ->
                budget.check()
                identity(node, path, budget) to node
            }
        val node = NodeActionLocatorMatcher.find(locator, candidates)
            ?: return NodeActionResolution.TargetMissing
        budget.check()
        val actions = NodeActionPolicy.resolve(
            actions = reportedActions(node),
            standardLabel = { AndroidNodeActionLabels.standardLabel(context, it) },
            isExcluded = { AndroidNodeActionLabels.isExcluded(it) || it in excludedActionIds }
        )
        return NodeActionResolution.Resolved(
            ResolvedNodeActionTarget(actions) { actionId ->
                coroutineContext.ensureActive()
                node.actionList.any { it.id == actionId } && node.performAction(actionId)
            }
        )
    }

}

internal fun identity(
    node: AccessibilityNodeInfo,
    childPath: List<Int>,
    budget: NodeProcessingBudget = NodeProcessingBudget()
): NodeActionIdentity {
    val bounds = Rect().also(node::getBoundsInScreen)
    return NodeActionIdentity(
        packageName = node.packageName?.toString(),
        windowId = node.windowId,
        childPath = childPath,
        bounds = NodeActionBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
        className = node.className?.toString(),
        text = budget.identityText(node.text),
        contentDescription = budget.identityText(node.contentDescription),
        viewIdResourceName = node.viewIdResourceName,
        uniqueId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) node.uniqueId else null
    )
}

internal fun reportedActions(node: AccessibilityNodeInfo): List<ReportedNodeAction> =
    node.actionList.orEmpty().map { ReportedNodeAction(it.id, it.label?.toString()) }

internal object AndroidNodeActionLabels {
    fun standardLabel(context: Context, actionId: Int): String? =
        standardLabelResource(actionId)?.let(context::getString)

    fun isExcluded(actionId: Int): Boolean = actionId in excludedStandardActionIds

    private fun standardLabelResource(actionId: Int): Int? {
        val resource = when (actionId) {
            AccessibilityNodeInfo.ACTION_CLICK -> R.string.accessibility_action_click
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> R.string.accessibility_action_long_click
            AccessibilityNodeInfo.AccessibilityAction.ACTION_CONTEXT_CLICK.id -> R.string.accessibility_action_context_click
            AccessibilityNodeInfo.ACTION_SELECT -> R.string.accessibility_action_select
            AccessibilityNodeInfo.ACTION_CLEAR_SELECTION -> R.string.accessibility_action_clear_selection
            AccessibilityNodeInfo.ACTION_EXPAND -> R.string.accessibility_action_expand
            AccessibilityNodeInfo.ACTION_COLLAPSE -> R.string.accessibility_action_collapse
            AccessibilityNodeInfo.ACTION_DISMISS -> R.string.accessibility_action_dismiss
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> R.string.accessibility_action_scroll_forward
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> R.string.accessibility_action_scroll_backward
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id -> R.string.accessibility_action_scroll_up
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id -> R.string.accessibility_action_scroll_down
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id -> R.string.accessibility_action_scroll_left
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id -> R.string.accessibility_action_scroll_right
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id -> R.string.accessibility_action_page_up
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.id -> R.string.accessibility_action_page_down
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT.id -> R.string.accessibility_action_page_left
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT.id -> R.string.accessibility_action_page_right
            AccessibilityNodeInfo.ACTION_COPY -> R.string.accessibility_action_copy
            AccessibilityNodeInfo.ACTION_CUT -> R.string.accessibility_action_cut
            AccessibilityNodeInfo.ACTION_PASTE -> R.string.accessibility_action_paste
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_TOOLTIP.id -> R.string.accessibility_action_show_tooltip
            AccessibilityNodeInfo.AccessibilityAction.ACTION_HIDE_TOOLTIP.id -> R.string.accessibility_action_hide_tooltip
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id -> R.string.accessibility_action_show_on_screen
            else -> null
        }
        if (resource != null) return resource
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            actionId == AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        ) {
            R.string.accessibility_action_ime_enter
        } else {
            null
        }
    }

    private val excludedStandardActionIds = buildSet {
        add(AccessibilityNodeInfo.ACTION_FOCUS)
        add(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
        add(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        add(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        add(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
        add(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
        add(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT)
        add(AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT)
        add(AccessibilityNodeInfo.ACTION_SET_SELECTION)
        add(AccessibilityNodeInfo.ACTION_SET_TEXT)
        add(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id)
        add(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id)
        add(AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(AccessibilityNodeInfo.AccessibilityAction.ACTION_PRESS_AND_HOLD.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            add(AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START.id)
            add(AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_DROP.id)
            add(AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_CANCEL.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION.id)
        }
    }
}
