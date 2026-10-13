package com.enaboapps.switchify.service.techniques.radar.sweep

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarSweepGeometryTest {
    @Test
    fun fullScreenPivotSitsOnTheChosenEdge() {
        val bounds = rect(0, 0, 1080, 2340)

        val fromTop = RadarSweepGeometry(bounds, RadarPivot.TOP)
        assertEquals(540f, fromTop.pivotX)
        assertEquals(0f, fromTop.pivotY)

        val fromBottom = RadarSweepGeometry(bounds, RadarPivot.BOTTOM)
        assertEquals(540f, fromBottom.pivotX)
        assertEquals(2340f, fromBottom.pivotY)

        val fromCenter = RadarSweepGeometry(bounds, RadarPivot.CENTER)
        assertEquals(540f, fromCenter.pivotX)
        assertEquals(1170f, fromCenter.pivotY)
    }

    @Test
    fun blockPivotIsRelativeToTheBlockNotTheScreen() {
        val block = rect(540, 1170, 810, 1755)

        val fromTop = RadarSweepGeometry(block, RadarPivot.TOP)
        assertEquals(675f, fromTop.pivotX)
        assertEquals(1170f, fromTop.pivotY)

        val fromCenter = RadarSweepGeometry(block, RadarPivot.CENTER)
        assertEquals(675f, fromCenter.pivotX)
        assertEquals(1462.5f, fromCenter.pivotY)
    }

    @Test
    fun maxDistanceReachesTheFarCorner() {
        val bounds = rect(0, 0, 600, 800)

        assertEquals(854.4f, RadarSweepGeometry(bounds, RadarPivot.TOP).maxDistance, 0.1f)
        assertEquals(854.4f, RadarSweepGeometry(bounds, RadarPivot.BOTTOM).maxDistance, 0.1f)
        assertEquals(500f, RadarSweepGeometry(bounds, RadarPivot.CENTER).maxDistance, 0.1f)
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
