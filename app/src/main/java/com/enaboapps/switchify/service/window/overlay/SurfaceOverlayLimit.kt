package com.enaboapps.switchify.service.window.overlay

internal object SurfaceOverlayLimit {
    const val MAX_ACTIVE_SURFACE_OVERLAYS = 8

    fun canAddSurfaceOverlay(activeCount: Int): Boolean {
        return activeCount < MAX_ACTIVE_SURFACE_OVERLAYS
    }
}
