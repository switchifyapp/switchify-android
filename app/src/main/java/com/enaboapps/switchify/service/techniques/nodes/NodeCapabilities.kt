package com.enaboapps.switchify.service.techniques.nodes

internal data class NodeCapabilities(
    val role: NodeRole,
    val isKeyboardNode: Boolean,
    val isClickable: Boolean,
    val isLongClickable: Boolean,
    val isFocusable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isTextSelectable: Boolean,
    val hasClickAction: Boolean,
    val hasLongClickAction: Boolean,
    val hasFocusAction: Boolean,
    val hasSetTextAction: Boolean,
    val hasScrollForwardAction: Boolean,
    val hasScrollBackwardAction: Boolean,
    val hasCutAction: Boolean,
    val hasCopyAction: Boolean,
    val hasPasteAction: Boolean,
    val hasCustomActions: Boolean,
    val hasCollectionInfo: Boolean,
    val hasCollectionItemInfo: Boolean,
    val hasText: Boolean,
    val hasContentDescription: Boolean,
    val hasUsableScreenBounds: Boolean,
    val hasUsableWindowBounds: Boolean,
    val collectionMetadata: NodeCollectionMetadata? = null
) {
    val isCurrentlyScannable: Boolean
        get() = isClickable || isLongClickable || isFocusable || hasClickAction

    val prefersAccessibilityClickForSelection: Boolean
        get() = hasClickAction && !isKeyboardNode
}
