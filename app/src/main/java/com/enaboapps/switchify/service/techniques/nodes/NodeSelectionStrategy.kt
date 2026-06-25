package com.enaboapps.switchify.service.techniques.nodes

import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.GestureRepeatManager

internal enum class NodeSelectionReason {
    AccessibilityClickPreferred,
    KeyboardNodeTap,
    GestureRepeatTap,
    GestureLockTap,
    NoClickActionTap
}

internal data class NodeSelectionDecision(
    val preferAccessibilityClick: Boolean,
    val reason: NodeSelectionReason
)

internal data class NodeSelectionRuntimeState(
    val isGestureRepeatEnabled: Boolean = false,
    val isGestureRepeatSessionActive: Boolean = false,
    val isGestureLockEnabled: Boolean = false,
    val isGestureLockEngaged: Boolean = false
) {
    val needsGestureRepeatTap: Boolean
        get() = isGestureRepeatEnabled || isGestureRepeatSessionActive

    val needsGestureLockTap: Boolean
        get() = isGestureLockEnabled || isGestureLockEngaged
}

internal object NodeSelectionStrategy {
    private var runtimeStateProviderForTesting: (() -> NodeSelectionRuntimeState)? = null

    fun decide(capabilities: NodeCapabilities?): NodeSelectionDecision {
        val runtimeState = runtimeState()
        return when {
            runtimeState.needsGestureRepeatTap -> NodeSelectionDecision(
                preferAccessibilityClick = false,
                reason = NodeSelectionReason.GestureRepeatTap
            )

            runtimeState.needsGestureLockTap -> NodeSelectionDecision(
                preferAccessibilityClick = false,
                reason = NodeSelectionReason.GestureLockTap
            )

            capabilities?.isKeyboardNode == true -> NodeSelectionDecision(
                preferAccessibilityClick = false,
                reason = NodeSelectionReason.KeyboardNodeTap
            )

            capabilities?.prefersAccessibilityClickForSelection == true -> NodeSelectionDecision(
                preferAccessibilityClick = true,
                reason = NodeSelectionReason.AccessibilityClickPreferred
            )

            else -> NodeSelectionDecision(
                preferAccessibilityClick = false,
                reason = NodeSelectionReason.NoClickActionTap
            )
        }
    }

    internal fun setRuntimeStateProviderForTesting(provider: (() -> NodeSelectionRuntimeState)?) {
        runtimeStateProviderForTesting = provider
    }

    private fun runtimeState(): NodeSelectionRuntimeState {
        runtimeStateProviderForTesting?.let { return it() }
        return NodeSelectionRuntimeState(
            isGestureRepeatEnabled = GestureRepeatManager.instance.isAutoRepeatEnabled(),
            isGestureRepeatSessionActive = GestureRepeatManager.instance.isRepeatSessionActive(),
            isGestureLockEnabled = GestureLockManager.instance.isLocked(),
            isGestureLockEngaged = GestureLockManager.instance.isGestureLockEngaged()
        )
    }
}
