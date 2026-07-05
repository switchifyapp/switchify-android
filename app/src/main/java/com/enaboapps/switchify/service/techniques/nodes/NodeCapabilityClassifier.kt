package com.enaboapps.switchify.service.techniques.nodes

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

internal data class RawNodeCapabilityFacts(
    val isKeyboardNode: Boolean = false,
    val isClickable: Boolean = false,
    val isLongClickable: Boolean = false,
    val isFocusable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
    val isTextSelectable: Boolean = false,
    val actionIds: Set<Int> = emptySet(),
    val hasCollectionInfo: Boolean = false,
    val hasCollectionItemInfo: Boolean = false,
    val hasText: Boolean = false,
    val hasContentDescription: Boolean = false,
    val hasUsableScreenBounds: Boolean = false,
    val hasUsableWindowBounds: Boolean = false,
    val collectionMetadata: CollectionMetadata? = null,
    val collectionItemMetadata: CollectionItemMetadata? = null
)

internal object NodeCapabilityClassifier {
    const val ACTION_ID_CLICK = 16
    const val ACTION_ID_LONG_CLICK = 32
    const val ACTION_ID_FOCUS = 1
    const val ACTION_ID_SET_TEXT = 2_097_152
    const val ACTION_ID_SCROLL_FORWARD = 4_096
    const val ACTION_ID_SCROLL_BACKWARD = 8_192
    const val ACTION_ID_CUT = 16_384
    const val ACTION_ID_COPY = 16_384 * 2
    const val ACTION_ID_PASTE = 16_384 * 4

    private val knownActionIds = setOf(
        ACTION_ID_CLICK,
        ACTION_ID_LONG_CLICK,
        ACTION_ID_FOCUS,
        ACTION_ID_SET_TEXT,
        ACTION_ID_SCROLL_FORWARD,
        ACTION_ID_SCROLL_BACKWARD,
        ACTION_ID_CUT,
        ACTION_ID_COPY,
        ACTION_ID_PASTE
    )

    fun classify(
        nodeInfo: AccessibilityNodeInfo,
        boundsInScreen: Rect,
        boundsInWindow: Rect?
    ): NodeCapabilities {
        val collectionInfo = runCatching { nodeInfo.collectionInfo }.getOrNull()
        val collectionItemInfo = runCatching { nodeInfo.collectionItemInfo }.getOrNull()
        return classifyFacts(
            RawNodeCapabilityFacts(
                isKeyboardNode = runCatching {
                    nodeInfo.window?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
                }.getOrDefault(false),
                isClickable = nodeInfo.isClickable,
                isLongClickable = nodeInfo.isLongClickable,
                isFocusable = nodeInfo.isFocusable,
                isEditable = nodeInfo.isEditable,
                isScrollable = nodeInfo.isScrollable,
                isCheckable = nodeInfo.isCheckable,
                isChecked = nodeInfo.isCheckedCompat(),
                isTextSelectable = nodeInfo.isTextSelectableCompat(),
                actionIds = nodeInfo.actionList?.map { it.id }?.toSet().orEmpty(),
                hasCollectionInfo = collectionInfo != null,
                hasCollectionItemInfo = collectionItemInfo != null,
                hasText = !nodeInfo.text.isNullOrBlank(),
                hasContentDescription = !nodeInfo.contentDescription.isNullOrBlank(),
                hasUsableScreenBounds = boundsInScreen.hasUsableHighlightBounds(),
                hasUsableWindowBounds = boundsInWindow.hasUsableHighlightBounds(),
                collectionMetadata = collectionInfo?.toMetadata(),
                collectionItemMetadata = collectionItemInfo?.toMetadata()
            )
        )
    }

    fun classifyFacts(facts: RawNodeCapabilityFacts): NodeCapabilities {
        val hasClickAction = facts.actionIds.contains(ACTION_ID_CLICK)
        val hasLongClickAction = facts.actionIds.contains(ACTION_ID_LONG_CLICK)
        val hasFocusAction = facts.actionIds.contains(ACTION_ID_FOCUS)
        val hasSetTextAction = facts.actionIds.contains(ACTION_ID_SET_TEXT)
        val hasScrollForwardAction = facts.actionIds.contains(ACTION_ID_SCROLL_FORWARD)
        val hasScrollBackwardAction = facts.actionIds.contains(ACTION_ID_SCROLL_BACKWARD)
        val hasCutAction = facts.actionIds.contains(ACTION_ID_CUT)
        val hasCopyAction = facts.actionIds.contains(ACTION_ID_COPY)
        val hasPasteAction = facts.actionIds.contains(ACTION_ID_PASTE)
        val hasCustomActions = facts.actionIds.any { it !in knownActionIds }

        return NodeCapabilities(
            role = primaryRole(
                facts = facts,
                hasClickAction = hasClickAction,
                hasFocusAction = hasFocusAction,
                hasSetTextAction = hasSetTextAction,
                hasScrollForwardAction = hasScrollForwardAction,
                hasScrollBackwardAction = hasScrollBackwardAction
            ),
            isKeyboardNode = facts.isKeyboardNode,
            isClickable = facts.isClickable,
            isLongClickable = facts.isLongClickable,
            isFocusable = facts.isFocusable,
            isEditable = facts.isEditable,
            isScrollable = facts.isScrollable,
            isCheckable = facts.isCheckable,
            isChecked = facts.isChecked,
            isTextSelectable = facts.isTextSelectable,
            hasClickAction = hasClickAction,
            hasLongClickAction = hasLongClickAction,
            hasFocusAction = hasFocusAction,
            hasSetTextAction = hasSetTextAction,
            hasScrollForwardAction = hasScrollForwardAction,
            hasScrollBackwardAction = hasScrollBackwardAction,
            hasCutAction = hasCutAction,
            hasCopyAction = hasCopyAction,
            hasPasteAction = hasPasteAction,
            hasCustomActions = hasCustomActions,
            hasCollectionInfo = facts.hasCollectionInfo,
            hasCollectionItemInfo = facts.hasCollectionItemInfo,
            hasText = facts.hasText,
            hasContentDescription = facts.hasContentDescription,
            hasUsableScreenBounds = facts.hasUsableScreenBounds,
            hasUsableWindowBounds = facts.hasUsableWindowBounds,
            collectionMetadata = NodeCollectionMetadata(
                collection = facts.collectionMetadata,
                collectionItem = facts.collectionItemMetadata
            ).takeIf {
                it.collection != null || it.collectionItem != null
            }
        )
    }

    private fun primaryRole(
        facts: RawNodeCapabilityFacts,
        hasClickAction: Boolean,
        hasFocusAction: Boolean,
        hasSetTextAction: Boolean,
        hasScrollForwardAction: Boolean,
        hasScrollBackwardAction: Boolean
    ): NodeRole {
        return when {
            facts.isKeyboardNode -> NodeRole.KeyboardKey
            facts.isEditable || hasSetTextAction -> NodeRole.Editable
            facts.isScrollable || hasScrollForwardAction || hasScrollBackwardAction -> NodeRole.Scrollable
            facts.isCheckable -> NodeRole.Checkable
            facts.hasCollectionInfo -> NodeRole.Collection
            facts.hasCollectionItemInfo -> NodeRole.CollectionItem
            facts.isClickable || hasClickAction -> NodeRole.Clickable
            facts.isFocusable || hasFocusAction -> NodeRole.Focusable
            facts.hasText || facts.hasContentDescription -> NodeRole.TextOnly
            else -> NodeRole.Unknown
        }
    }
}

private fun AccessibilityNodeInfo.CollectionInfo.toMetadata(): CollectionMetadata? {
    return runCatching {
        CollectionMetadata(
            rowCount = rowCount,
            columnCount = columnCount,
            isHierarchical = isHierarchical,
            selectionMode = selectionMode
        )
    }.getOrNull()
}

@Suppress("DEPRECATION")
private fun AccessibilityNodeInfo.CollectionItemInfo.toMetadata(): CollectionItemMetadata? {
    return runCatching {
        CollectionItemMetadata(
            rowIndex = rowIndex,
            rowSpan = rowSpan,
            columnIndex = columnIndex,
            columnSpan = columnSpan,
            isHeading = isHeading,
            isSelected = isSelected
        )
    }.getOrNull()
}

private fun Rect?.hasUsableHighlightBounds(): Boolean {
    return this != null && right > left && bottom > top
}

@Suppress("DEPRECATION")
private fun AccessibilityNodeInfo.isCheckedCompat(): Boolean {
    return isChecked
}

private fun AccessibilityNodeInfo.isTextSelectableCompat(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { isTextSelectable }.getOrDefault(false)
    } else {
        false
    }
}
