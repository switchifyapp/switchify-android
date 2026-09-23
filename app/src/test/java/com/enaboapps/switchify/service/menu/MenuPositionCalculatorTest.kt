package com.enaboapps.switchify.service.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuPositionCalculatorTest {
    @Test
    fun horizontalPositionIsClampedInsideBothScreenEdges() {
        assertEquals(0, MenuHorizontalPositionCalculator.clamp(-80, 300, 900))
        assertEquals(600, MenuHorizontalPositionCalculator.clamp(750, 300, 900))
        assertEquals(240, MenuHorizontalPositionCalculator.clamp(240, 300, 900))
    }

    @Test
    fun oversizedMenuFallsBackToTheLeftScreenEdge() {
        assertEquals(0, MenuHorizontalPositionCalculator.clamp(200, 1000, 900))
    }
}
