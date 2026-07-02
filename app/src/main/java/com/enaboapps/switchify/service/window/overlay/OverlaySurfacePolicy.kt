package com.enaboapps.switchify.service.window.overlay

internal object OverlaySurfacePolicy {
    fun shouldUseSurfaceBackend(target: OverlayTarget): Boolean {
        return when (target) {
            is OverlayTarget.Display -> target.displayId != OverlayTargets.DEFAULT_DISPLAY_ID
            is OverlayTarget.Window -> true
        }
    }
}
