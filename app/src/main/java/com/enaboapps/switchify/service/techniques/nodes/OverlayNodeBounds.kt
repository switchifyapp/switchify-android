package com.enaboapps.switchify.service.techniques.nodes

import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets

data class OverlayNodeBounds(
    val displayId: Int,
    val windowId: Int?,
    val windowType: Int?,
    val boundsInScreen: Rect,
    val boundsInWindow: Rect?,
    val forceDisplaySurface: Boolean = false
) {
    fun target(): OverlayTarget {
        return if (windowId != null && windowType != null && boundsInWindow.isUsableHighlightBounds()) {
            OverlayTarget.Window(
                displayId = displayId,
                accessibilityWindowId = windowId,
                windowType = windowType,
                fallbackBoundsInScreen = boundsInScreen.copy()
            )
        } else {
            OverlayTarget.Display(displayId, forceSurface = forceDisplaySurface)
        }
    }

    fun highlightBounds(target: OverlayTarget = target()): Rect {
        return if (target is OverlayTarget.Window && boundsInWindow.isUsableHighlightBounds()) {
            boundsInWindow.copy()
        } else {
            boundsInScreen.copy()
        }
    }

    fun isInputMethodWindow(): Boolean {
        return windowType == AccessibilityWindowInfo.TYPE_INPUT_METHOD
    }

    companion object {
        fun displayOnly(
            boundsInScreen: Rect,
            forceSurface: Boolean = false
        ): OverlayNodeBounds {
            return OverlayNodeBounds(
                displayId = OverlayTargets.DEFAULT_DISPLAY_ID,
                windowId = null,
                windowType = null,
                boundsInScreen = boundsInScreen.copy(),
                boundsInWindow = null,
                forceDisplaySurface = forceSurface
            )
        }
    }
}

private fun Rect?.copy(): Rect {
    return Rect().also { copy ->
        if (this != null) {
            copy.left = left
            copy.top = top
            copy.right = right
            copy.bottom = bottom
        }
    }
}

private fun Rect?.isUsableHighlightBounds(): Boolean {
    return this != null && right > left && bottom > top
}
