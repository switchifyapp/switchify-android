package com.enaboapps.switchify.service.window.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceOverlayLimitTest {
    @Test
    fun allowsOverlayBelowCap() {
        assertTrue(
            SurfaceOverlayLimit.canAddSurfaceOverlay(
                SurfaceOverlayLimit.MAX_ACTIVE_SURFACE_OVERLAYS - 1
            )
        )
    }

    @Test
    fun rejectsOverlayAtCap() {
        assertFalse(
            SurfaceOverlayLimit.canAddSurfaceOverlay(
                SurfaceOverlayLimit.MAX_ACTIVE_SURFACE_OVERLAYS
            )
        )
    }
}
