package com.enaboapps.switchify.service.keyboard

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Handles extraction and filtering of keyboard nodes from accessibility tree.
 *
 * This class provides a clear separation of concerns for keyboard-specific
 * node extraction logic, making it easier to test and maintain keyboard
 * scanning functionality independently from general node examination.
 *
 * Responsibilities:
 * - Extract keyboard root node
 * - Provide keyboard window information
 * - (Future) Filter keyboard-specific nodes with custom logic
 *
 * Keyboard visibility itself is owned by KeyboardManager — read it from
 * `KeyboardManager.keyboardState.value.isVisible`.
 */
class KeyboardNodeExtractor {

    /**
     * Gets the keyboard window if visible.
     *
     * @param windows List of accessibility windows to search
     * @return AccessibilityWindowInfo for keyboard, or null if not present
     */
    fun getKeyboardWindow(windows: List<AccessibilityWindowInfo>): AccessibilityWindowInfo? {
        return windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }

    /**
     * Gets the root node of the keyboard window.
     *
     * This is the entry point for traversing the keyboard's accessibility tree.
     *
     * @param windows List of accessibility windows to search
     * @return Root AccessibilityNodeInfo of keyboard window, or null if not present
     */
    fun getKeyboardRootNode(windows: List<AccessibilityWindowInfo>): AccessibilityNodeInfo? {
        return getKeyboardWindow(windows)?.root
    }

    /**
     * Filters nodes specifically for keyboard scanning.
     *
     * Currently uses the same filtering as regular nodes, but provides
     * an extension point for keyboard-specific filtering logic in the future.
     *
     * Future enhancements could include:
     * - Different proximity thresholds for smaller keys
     * - Filtering duplicate adjacent keys (e.g., shift states)
     * - Prioritizing letter keys over symbols
     * - Adjusting node weights for better scanning order
     *
     * @param nodes List of nodes to filter
     * @return Filtered list of nodes suitable for keyboard scanning
     */
    fun filterKeyboardNodes(nodes: List<Any>): List<Any> {
        // For now, use same filtering as regular nodes
        // This method exists to provide a clear extension point for future enhancements
        return nodes
    }
}
