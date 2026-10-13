package com.enaboapps.switchify.service.techniques.radar.sweep

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarSweepGeometryTest {
    @Test
    fun fullScreenPivotSitsOnTheChosenEdge() {
        val bounds = rect(0, 0, 1080, 2340)

        val fromTop = RadarSweepGeometry(bounds, pivotFromTop = true)
        assertEquals(540f, fromTop.pivotX)
        assertEquals(0f, fromTop.pivotY)

        val fromBottom = RadarSweepGeometry(bounds, pivotFromTop = false)
        assertEquals(540f, fromBottom.pivotX)
        assertEquals(2340f, fromBottom.pivotY)
    }

    @Test
    fun blockPivotIsRelativeToTheBlockNotTheScreen() {
        val geometry = RadarSweepGeometry(rect(540, 1170, 810, 1755), pivotFromTop = true)

        assertEquals(675f, geometry.pivotX)
        assertEquals(1170f, geometry.pivotY)
    }

    @Test
    fun maxDistanceReachesTheFarCorner() {
        val geometry = RadarSweepGeometry(rect(0, 0, 600, 800), pivotFromTop = true)

        assertEquals(854.4f, geometry.maxDistance, 0.1f)
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
