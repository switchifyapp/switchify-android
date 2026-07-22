package com.enaboapps.switchify.service.techniques.nodes.scanners

import com.enaboapps.switchify.service.window.overlay.OverlayTarget

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
    val target: OverlayTarget
)

internal data class NodeScannerHighlightState(
    val role: NodeScannerHighlightRole,
    val target: OverlayTarget
)

internal enum class NodeScannerHighlightTransition {
    ATTACH,
    UPDATE,
    REPLACE_TARGET,
    REMOVE,
    IGNORE
}

internal object NodeScannerHighlightTransitions {
    fun show(
        current: NodeScannerHighlightState?,
        next: NodeScannerHighlightSpec
    ): NodeScannerHighlightTransition {
        return when {
            current == null -> NodeScannerHighlightTransition.ATTACH
            current.target == next.target -> NodeScannerHighlightTransition.UPDATE
            else -> NodeScannerHighlightTransition.REPLACE_TARGET
        }
    }

    fun hide(
        current: NodeScannerHighlightState?,
        roles: Set<NodeScannerHighlightRole>
    ): NodeScannerHighlightTransition {
        return if (current?.role in roles) {
            NodeScannerHighlightTransition.REMOVE
        } else {
            NodeScannerHighlightTransition.IGNORE
        }
    }

    fun isCurrentEpoch(commandEpoch: Long, rendererEpoch: Long): Boolean {
        return commandEpoch == rendererEpoch
    }
}
