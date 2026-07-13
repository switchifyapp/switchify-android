package com.enaboapps.switchify.service.gestures.visuals

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureVisualTokensTest {
    @Test
    fun resolvesAnimatedAndStaticMotionModes() {
        assertEquals(
            GestureVisualMotionMode.ANIMATED,
            GestureVisualMotionModeResolver.resolve(animatorsEnabled = true)
        )
        assertEquals(
            GestureVisualMotionMode.STATIC,
            GestureVisualMotionModeResolver.resolve(animatorsEnabled = false)
        )
    }
}
