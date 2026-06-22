package com.enaboapps.switchify.service.window.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayTargetTest {
    @Test
    fun defaultDisplayUsesStableDisplayId() {
        assertEquals(
            OverlayTarget.Display(OverlayTargets.DEFAULT_DISPLAY_ID),
            OverlayTargets.defaultDisplay()
        )
    }

    @Test
    fun windowTargetCarriesDisplayWindowAndType() {
        val target = OverlayTarget.Window(
            displayId = 2,
            accessibilityWindowId = 42,
            windowType = 3
        )

        assertEquals(2, target.displayId)
        assertEquals(42, target.accessibilityWindowId)
        assertEquals(3, target.windowType)
    }

    @Test
    fun displayFallbackPreservesDisplayTarget() {
        val target = OverlayTarget.Display(4)

        assertEquals(target, OverlayTargets.displayFallback(target))
    }

    @Test
    fun displayFallbackUsesWindowDisplayId() {
        val target = OverlayTarget.Window(
            displayId = 5,
            accessibilityWindowId = 42,
            windowType = 3
        )

        assertEquals(OverlayTarget.Display(5), OverlayTargets.displayFallback(target))
    }

    @Test
    fun boundsPlacementCarriesAbsoluteGeometry() {
        val placement = OverlayPlacement.Bounds(
            x = 10,
            y = 20,
            width = 30,
            height = 40
        )

        assertEquals(10, placement.x)
        assertEquals(20, placement.y)
        assertEquals(30, placement.width)
        assertEquals(40, placement.height)
    }
}
