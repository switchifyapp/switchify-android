package com.enaboapps.switchify.service.gestures.execution

import org.junit.Assert.assertEquals
import org.junit.Test

class PinchGestureGeometryTest {
    @Test
    fun expandingPinchMovesBothTouchesAwayFromCenter() {
        val geometry = PinchGestureGeometry.calculate(300f, 500f, expands = true)

        assertEquals(GesturePathPoint(275f, 500f), geometry.first.start)
        assertEquals(GesturePathPoint(200f, 500f), geometry.first.end)
        assertEquals(GesturePathPoint(325f, 500f), geometry.second.start)
        assertEquals(GesturePathPoint(400f, 500f), geometry.second.end)
    }

    @Test
    fun contractingPinchMovesBothTouchesTowardCenter() {
        val geometry = PinchGestureGeometry.calculate(300f, 500f, expands = false)

        assertEquals(GesturePathPoint(200f, 500f), geometry.first.start)
        assertEquals(GesturePathPoint(275f, 500f), geometry.first.end)
        assertEquals(GesturePathPoint(400f, 500f), geometry.second.start)
        assertEquals(GesturePathPoint(325f, 500f), geometry.second.end)
    }

    @Test
    fun interpolationPreservesDirectionAndBoundsProgress() {
        val start = GesturePathPoint(20f, 40f)
        val end = GesturePathPoint(120f, 240f)

        assertEquals(start, GesturePathMath.interpolate(start, end, -1f))
        assertEquals(GesturePathPoint(45f, 90f), GesturePathMath.interpolate(start, end, 0.25f))
        assertEquals(end, GesturePathMath.interpolate(start, end, 2f))
    }
}
