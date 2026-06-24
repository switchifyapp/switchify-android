package com.enaboapps.switchify.service.keyboard

import android.graphics.Rect
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardWindowTargetTest {
    @Test
    fun convertsToWindowOverlayTarget() {
        val target = KeyboardWindowTarget(
            displayId = 2,
            windowId = 8,
            windowType = 4,
            boundsInScreen = rect(1, 2, 3, 4)
        )

        val overlayTarget: OverlayTarget = target.toOverlayTarget()
        assertTrue(overlayTarget is OverlayTarget.Window)
        overlayTarget as OverlayTarget.Window
        assertEquals(2, overlayTarget.displayId)
        assertEquals(8, overlayTarget.accessibilityWindowId)
        assertEquals(4, overlayTarget.windowType)
        assertEquals(1, overlayTarget.fallbackBoundsInScreen?.left)
        assertEquals(2, overlayTarget.fallbackBoundsInScreen?.top)
        assertEquals(3, overlayTarget.fallbackBoundsInScreen?.right)
        assertEquals(4, overlayTarget.fallbackBoundsInScreen?.bottom)
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
