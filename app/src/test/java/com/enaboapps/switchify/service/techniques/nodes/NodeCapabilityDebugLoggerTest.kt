package com.enaboapps.switchify.service.techniques.nodes

import android.graphics.Rect
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeCapabilityDebugLoggerTest {
    @Test
    fun buildMessageIncludesCapabilitySummary() {
        val message = NodeCapabilityDebugLogger.buildMessage(
            action = "highlight",
            capabilities = NodeCapabilities(
                role = NodeRole.Clickable,
                isKeyboardNode = false,
                isClickable = true,
                isLongClickable = false,
                isFocusable = true,
                isEditable = false,
                isScrollable = false,
                isCheckable = false,
                isChecked = false,
                isTextSelectable = false,
                hasClickAction = true,
                hasLongClickAction = false,
                hasFocusAction = true,
                hasSetTextAction = false,
                hasScrollForwardAction = false,
                hasScrollBackwardAction = false,
                hasCutAction = false,
                hasCopyAction = false,
                hasPasteAction = false,
                hasCustomActions = true,
                hasCollectionInfo = false,
                hasCollectionItemInfo = true,
                hasText = true,
                hasContentDescription = true,
                hasUsableScreenBounds = true,
                hasUsableWindowBounds = false
            ),
            bounds = rect(1, 2, 30, 40),
            elementType = "Button",
            description = "Open details"
        )

        assertTrue(message.contains("action=highlight"))
        assertTrue(message.contains("role=Clickable"))
        assertTrue(message.contains("scannable=true"))
        assertTrue(message.contains("preferAccessibilityClick=true"))
        assertTrue(message.contains("screenBounds=1,2,30,40"))
        assertTrue(message.contains("usableWindowBounds=false"))
        assertTrue(message.contains("customActions=true"))
        assertTrue(message.contains("collectionItem=true"))
        assertTrue(message.contains("elementType=Button"))
        assertTrue(message.contains("description=Open details"))
    }

    @Test
    fun buildMessageSanitizesDescriptionWhitespace() {
        val message = NodeCapabilityDebugLogger.buildMessage(
            action = "select",
            capabilities = null,
            bounds = rect(0, 0, 10, 10),
            elementType = null,
            description = "Line one\n\tLine two"
        )

        assertTrue(message.contains("role=Synthetic"))
        assertTrue(message.contains("elementType=unknown"))
        assertTrue(message.contains("description=Line one Line two"))
    }

    private fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }
}
