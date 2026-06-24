package com.enaboapps.switchify.service.techniques.nodes

import android.graphics.Rect
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayNodeBoundsTest {
    @Test
    fun usesDisplayTargetWhenWindowMetadataIsMissing() {
        val bounds = OverlayNodeBounds(
            displayId = 2,
            windowId = null,
            windowType = null,
            boundsInScreen = rect(1, 2, 11, 12),
            boundsInWindow = null
        )

        assertEquals(OverlayTarget.Display(2), bounds.target())
    }

    @Test
    fun usesWindowTargetAndWindowBoundsWhenCompleteWindowMetadataExists() {
        val bounds = OverlayNodeBounds(
            displayId = 3,
            windowId = 7,
            windowType = 4,
            boundsInScreen = rect(100, 200, 130, 240),
            boundsInWindow = rect(10, 20, 40, 60)
        )

        val target = bounds.target()
        assertTrue(target is OverlayTarget.Window)
        target as OverlayTarget.Window
        assertEquals(3, target.displayId)
        assertEquals(7, target.accessibilityWindowId)
        assertEquals(4, target.windowType)
        assertEquals(100, target.fallbackBoundsInScreen?.left)
        assertEquals(200, target.fallbackBoundsInScreen?.top)
        assertEquals(130, target.fallbackBoundsInScreen?.right)
        assertEquals(240, target.fallbackBoundsInScreen?.bottom)
    }

    @Test
    fun usesDisplayTargetWhenWindowBoundsAreEmpty() {
        val bounds = OverlayNodeBounds(
            displayId = 3,
            windowId = 7,
            windowType = 4,
            boundsInScreen = rect(100, 200, 130, 240),
            boundsInWindow = rect(0, 0, 0, 0)
        )

        assertEquals(OverlayTarget.Display(3), bounds.target())
    }

    @Test
    fun usesDisplayTargetWhenWindowBoundsHaveNoWidth() {
        val bounds = OverlayNodeBounds(
            displayId = 3,
            windowId = 7,
            windowType = 4,
            boundsInScreen = rect(100, 200, 130, 240),
            boundsInWindow = rect(10, 20, 10, 60)
        )

        assertEquals(OverlayTarget.Display(3), bounds.target())
    }

    @Test
    fun usesDisplayTargetWhenWindowBoundsHaveNoHeight() {
        val bounds = OverlayNodeBounds(
            displayId = 3,
            windowId = 7,
            windowType = 4,
            boundsInScreen = rect(100, 200, 130, 240),
            boundsInWindow = rect(10, 20, 40, 20)
        )

        assertEquals(OverlayTarget.Display(3), bounds.target())
    }

    @Test
    fun highlightBoundsFallsBackToScreenBoundsForInvalidWindowBounds() {
        val screenBounds = rect(100, 200, 130, 240)
        val bounds = OverlayNodeBounds(
            displayId = 3,
            windowId = 7,
            windowType = 4,
            boundsInScreen = screenBounds,
            boundsInWindow = rect(0, 0, 0, 0)
        )

        val highlightBounds = bounds.highlightBounds(
            OverlayTarget.Window(
                displayId = 3,
                accessibilityWindowId = 7,
                windowType = 4
            )
        )

        assertEquals(screenBounds.left, highlightBounds.left)
        assertEquals(screenBounds.top, highlightBounds.top)
        assertEquals(screenBounds.right, highlightBounds.right)
        assertEquals(screenBounds.bottom, highlightBounds.bottom)
    }

    @Test
    fun displayOnlyUsesDefaultDisplay() {
        val bounds = OverlayNodeBounds.displayOnly(rect(5, 6, 7, 8))

        assertEquals(OverlayTarget.Display(OverlayTargets.DEFAULT_DISPLAY_ID), bounds.target())
    }

    @Test
    fun displayOnlyCanForceSurfaceTarget() {
        val bounds = OverlayNodeBounds.displayOnly(
            boundsInScreen = rect(5, 6, 7, 8),
            forceSurface = true
        )

        assertEquals(
            OverlayTarget.Display(
                displayId = OverlayTargets.DEFAULT_DISPLAY_ID,
                forceSurface = true
            ),
            bounds.target()
        )
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
