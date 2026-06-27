package com.enaboapps.switchify.service.techniques.nodes

import android.graphics.Rect
import android.util.Log
import com.enaboapps.switchify.BuildConfig

internal object NodeCapabilityDebugLogger {
    private const val TAG = "NodeCapabilities"

    fun log(action: String, node: Node) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, buildMessage(action, node))
    }

    internal fun buildMessage(action: String, node: Node): String {
        return buildMessage(
            action = action,
            capabilities = node.getCapabilities(),
            bounds = node.getBounds(),
            elementType = node.getElementType(),
            description = node.getContentDescription()
        )
    }

    internal fun buildMessage(
        action: String,
        capabilities: NodeCapabilities?,
        bounds: Rect,
        elementType: String?,
        description: String
    ): String {
        return buildList {
            addAll(
                listOf(
                    "action=$action",
                    "role=${capabilities?.role ?: "Synthetic"}",
                    "scannable=${capabilities?.isCurrentlyScannable ?: false}",
                    "preferAccessibilityClick=${capabilities?.prefersAccessibilityClickForSelection ?: false}",
                    "keyboard=${capabilities?.isKeyboardNode ?: false}",
                    "screenBounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
                    "usableScreenBounds=${capabilities?.hasUsableScreenBounds ?: true}",
                    "usableWindowBounds=${capabilities?.hasUsableWindowBounds ?: false}",
                    "clickable=${capabilities?.isClickable ?: false}",
                    "longClickable=${capabilities?.isLongClickable ?: false}",
                    "focusable=${capabilities?.isFocusable ?: false}",
                    "editable=${capabilities?.isEditable ?: false}",
                    "scrollable=${capabilities?.isScrollable ?: false}",
                    "checkable=${capabilities?.isCheckable ?: false}",
                    "checked=${capabilities?.isChecked ?: false}",
                    "textSelectable=${capabilities?.isTextSelectable ?: false}",
                    "clickAction=${capabilities?.hasClickAction ?: false}",
                    "longClickAction=${capabilities?.hasLongClickAction ?: false}",
                    "focusAction=${capabilities?.hasFocusAction ?: false}",
                    "setTextAction=${capabilities?.hasSetTextAction ?: false}",
                    "scrollForwardAction=${capabilities?.hasScrollForwardAction ?: false}",
                    "scrollBackwardAction=${capabilities?.hasScrollBackwardAction ?: false}",
                    "cutAction=${capabilities?.hasCutAction ?: false}",
                    "copyAction=${capabilities?.hasCopyAction ?: false}",
                    "pasteAction=${capabilities?.hasPasteAction ?: false}",
                    "customActions=${capabilities?.hasCustomActions ?: false}",
                    "collection=${capabilities?.hasCollectionInfo ?: false}",
                    "collectionItem=${capabilities?.hasCollectionItemInfo ?: false}",
                )
            )
            capabilities?.collectionMetadata?.collection?.let { collection ->
                add("collectionRows=${collection.rowCount}")
                add("collectionColumns=${collection.columnCount}")
                add("collectionHierarchical=${collection.isHierarchical}")
                add("collectionSelectionMode=${collection.selectionMode}")
            }
            capabilities?.collectionMetadata?.collectionItem?.let { collectionItem ->
                add("collectionItemRow=${collectionItem.rowIndex}")
                add("collectionItemRowSpan=${collectionItem.rowSpan}")
                add("collectionItemColumn=${collectionItem.columnIndex}")
                add("collectionItemColumnSpan=${collectionItem.columnSpan}")
                add("collectionItemHeading=${collectionItem.isHeading}")
                add("collectionItemSelected=${collectionItem.isSelected}")
            }
            addAll(
                listOf(
                    "hasText=${capabilities?.hasText ?: false}",
                    "hasContentDescription=${capabilities?.hasContentDescription ?: description.isNotBlank()}",
                    "elementType=${elementType ?: "unknown"}",
                    "description=${description.sanitizeForLog()}"
                )
            )
        }.joinToString(" ")
    }

    private fun String.sanitizeForLog(): String {
        return replace(Regex("\\s+"), " ")
            .take(80)
            .ifBlank { "none" }
    }
}
