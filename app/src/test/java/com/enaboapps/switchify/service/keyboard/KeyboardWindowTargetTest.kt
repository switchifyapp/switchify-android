package com.enaboapps.switchify.service.keyboard

import android.graphics.Rect
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardWindowTargetTest {
    @Test
    fun convertsToWindowOverlayTarget() {
        val target = KeyboardWindowTarget(
            displayId = 2,
            windowId = 8,
            windowType = 4,
            boundsInScreen = Rect(1, 2, 3, 4)
        )

        assertEquals(
            OverlayTarget.Window(
                displayId = 2,
                accessibilityWindowId = 8,
                windowType = 4
            ),
            target.toOverlayTarget()
        )
    }
}
