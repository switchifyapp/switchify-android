package com.enaboapps.switchify.service.techniques.nodes

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

internal object NodeSelectionPerformer {
    fun perform(
        accessibilityClick: () -> Boolean,
        fallbackTap: () -> Unit,
        preferAccessibilityClick: Boolean = true
    ): Boolean {
        if (!preferAccessibilityClick) {
            fallbackTap()
            return false
        }

        val clicked = runCatching { accessibilityClick() }.getOrDefault(false)
        if (clicked) return true

        fallbackTap()
        return false
    }

    fun perform(
        nodeInfo: AccessibilityNodeInfo?,
        fallbackTap: () -> Unit
    ): Boolean {
        return perform(
            accessibilityClick = {
                nodeInfo?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            },
            fallbackTap = fallbackTap,
            preferAccessibilityClick = !nodeInfo.isInputMethodNode()
        )
    }

    private fun AccessibilityNodeInfo?.isInputMethodNode(): Boolean {
        return runCatching {
            this?.window?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }.getOrDefault(false)
    }
}
