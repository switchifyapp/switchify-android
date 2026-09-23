package com.enaboapps.switchify.service.techniques.nodes.scanners

import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets

internal enum class NodeScannerHighlightRole {
    ITEM,
    ROW,
    ESCAPE
}

internal data class NodeScannerHighlightSpec(
    val role: NodeScannerHighlightRole,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val target: OverlayTarget,
    val owner: String? = null,
    val screenBounds: ScanHighlightBounds? = null,
    val intervalAfterSequence: Long = 0L
) {
    /**
     * True when this highlight may dim the rest of the display. The renderer
     * still has to confirm that [spotlightTarget] can actually attach.
     */
    fun usesSpotlight(requested: Boolean): Boolean =
        owner != null && requested && screenBounds?.isUsable == true

    /**
     * Spotlight draws in screen space, so it always lives on a display-wide
     * target: the root overlay on the default display, a surface elsewhere.
     */
    fun spotlightTarget(): OverlayTarget.Display {
        val displayId = when (val target = target) {
            is OverlayTarget.Display -> target.displayId
            is OverlayTarget.Window -> target.displayId
        }
        return OverlayTarget.Display(
            displayId,
            forceSurface = displayId != OverlayTargets.DEFAULT_DISPLAY_ID
        )
    }

    fun animatesFrom(previous: NodeScannerHighlightSpec, movementEnabled: Boolean,
        systemAnimationsEnabled: Boolean): Boolean = owner != null && previous.owner == owner &&
        movementEnabled && systemAnimationsEnabled &&
        NodeScannerHighlightTransitions.sameCoordinateSpace(previous.target, target)
}

internal data class NodeScannerHighlightState(
    val role: NodeScannerHighlightRole,
    val target: OverlayTarget,
    val owner: String? = null
)

internal enum class NodeScannerHighlightTransition {
    REMOVE,
    IGNORE
}

internal object NodeScannerHighlightTransitions {
    fun sameCoordinateSpace(first: OverlayTarget, second: OverlayTarget): Boolean = when {
        first is OverlayTarget.Display && second is OverlayTarget.Display -> first == second
        first is OverlayTarget.Window && second is OverlayTarget.Window ->
            first.displayId == second.displayId && first.accessibilityWindowId == second.accessibilityWindowId &&
                first.windowType == second.windowType
        else -> false
    }

    fun hide(
        current: NodeScannerHighlightState?,
        roles: Set<NodeScannerHighlightRole>,
        owner: String? = null
    ): NodeScannerHighlightTransition {
        return if (current?.role in roles && (owner == null || current?.owner == owner)) {
            NodeScannerHighlightTransition.REMOVE
        } else {
            NodeScannerHighlightTransition.IGNORE
        }
    }

    fun isCurrentEpoch(commandEpoch: Long, rendererEpoch: Long): Boolean {
        return commandEpoch == rendererEpoch
    }
}

internal class NodeScannerVisualBatch(val owner: String, val epoch: Long, private val intervalAfterSequence: Long = 0L) {
    var spec: NodeScannerHighlightSpec? = null
        private set
    val hideRoles = mutableSetOf<NodeScannerHighlightRole>()

    fun show(next: NodeScannerHighlightSpec) { spec = next.copy(intervalAfterSequence = intervalAfterSequence) }

    fun hide(roles: Set<NodeScannerHighlightRole>) {
        hideRoles.addAll(roles)
        if (spec?.role in roles) spec = null
    }

}
