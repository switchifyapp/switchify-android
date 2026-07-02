package com.enaboapps.switchify.service.window.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySurfacePolicyTest {
    @Test
    fun defaultDisplayDoesNotUseSurfaceBackend() {
        assertFalse(
            OverlaySurfacePolicy.shouldUseSurfaceBackend(
                OverlayTarget.Display(displayId = OverlayTargets.DEFAULT_DISPLAY_ID)
            )
        )
    }

    @Test
    fun defaultDisplayWithForceSurfaceDoesNotUseSurfaceBackend() {
        assertFalse(
            OverlaySurfacePolicy.shouldUseSurfaceBackend(
                OverlayTarget.Display(
                    displayId = OverlayTargets.DEFAULT_DISPLAY_ID,
                    forceSurface = true
                )
            )
        )
    }

    @Test
    fun nonDefaultDisplayUsesSurfaceBackend() {
        assertTrue(
            OverlaySurfacePolicy.shouldUseSurfaceBackend(
                OverlayTarget.Display(displayId = OverlayTargets.DEFAULT_DISPLAY_ID + 1)
            )
        )
    }

    @Test
    fun windowTargetUsesSurfaceBackend() {
        assertTrue(
            OverlaySurfacePolicy.shouldUseSurfaceBackend(
                OverlayTarget.Window(
                    displayId = OverlayTargets.DEFAULT_DISPLAY_ID,
                    accessibilityWindowId = 7,
                    windowType = 2
                )
            )
        )
    }
}
