package com.enaboapps.switchify.service.keyboard

import android.graphics.Rect
import com.enaboapps.switchify.service.window.overlay.OverlayTarget

data class KeyboardWindowTarget(
    val displayId: Int,
    val windowId: Int,
    val windowType: Int,
    val boundsInScreen: Rect
) {
    fun toOverlayTarget(): OverlayTarget.Window {
        return OverlayTarget.Window(
            displayId = displayId,
            accessibilityWindowId = windowId,
            windowType = windowType
        )
    }
}
