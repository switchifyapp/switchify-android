package com.enaboapps.switchify.service.window.overlay

import android.graphics.Rect

sealed class OverlayTarget {
    data class Display(
        val displayId: Int,
        val forceSurface: Boolean = false
    ) : OverlayTarget()

    data class Window(
        val displayId: Int,
        val accessibilityWindowId: Int,
        val windowType: Int,
        val fallbackBoundsInScreen: Rect? = null
    ) : OverlayTarget()
}

object OverlayTargets {
    const val DEFAULT_DISPLAY_ID = 0

    fun defaultDisplay(): OverlayTarget.Display {
        return OverlayTarget.Display(DEFAULT_DISPLAY_ID)
    }

    fun displayFallback(target: OverlayTarget): OverlayTarget.Display {
        return when (target) {
            is OverlayTarget.Display -> target
            is OverlayTarget.Window -> OverlayTarget.Display(
                displayId = target.displayId,
                forceSurface = true
            )
        }
    }
}
