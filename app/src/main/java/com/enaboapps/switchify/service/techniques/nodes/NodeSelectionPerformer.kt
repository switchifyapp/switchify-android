package com.enaboapps.switchify.service.techniques.nodes

import android.view.accessibility.AccessibilityNodeInfo

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
        preferAccessibilityClick: Boolean,
        fallbackTap: () -> Unit
    ): Boolean {
        return perform(
            accessibilityClick = {
                nodeInfo?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            },
            fallbackTap = fallbackTap,
            preferAccessibilityClick = preferAccessibilityClick
        )
    }
}
