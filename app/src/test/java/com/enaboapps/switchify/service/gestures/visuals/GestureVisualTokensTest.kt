package com.enaboapps.switchify.service.gestures.visuals

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureVisualTokensTest {
    @Test
    fun convertsDensityIndependentUnits() {
        val converter = GestureVisualUnitConverter(density = 2.5f, scaledDensity = 3f)

        assertEquals(60, converter.dp(24f))
        assertEquals(36f, converter.sp(12f), 0f)
    }

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
