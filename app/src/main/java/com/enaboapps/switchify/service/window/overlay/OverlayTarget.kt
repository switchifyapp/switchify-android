package com.enaboapps.switchify.service.window.overlay

sealed class OverlayTarget {
    data class Display(val displayId: Int) : OverlayTarget()

    data class Window(
        val displayId: Int,
        val accessibilityWindowId: Int,
        val windowType: Int
    ) : OverlayTarget()
}

object OverlayTargets {
    const val DEFAULT_DISPLAY_ID = 0

    fun defaultDisplay(): OverlayTarget.Display {
        return OverlayTarget.Display(DEFAULT_DISPLAY_ID)
    }
}
