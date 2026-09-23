package com.enaboapps.switchify.service.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuVerticalPositionCalculatorTest {

    @Test
    fun keepsPreferredPositionWhenItFits() {
        val y = MenuVerticalPositionCalculator.clamp(
            preferredY = 400,
            menuHeight = 500,
            screenHeight = 2000,
            topReserved = 100
        )

        assertEquals(400, y)
    }

    @Test
    fun clampsToBottomOfScreen() {
        val y = MenuVerticalPositionCalculator.clamp(
            preferredY = 1800,
            menuHeight = 500,
            screenHeight = 2000,
            topReserved = 100
        )

        assertEquals(1500, y)
    }

    @Test
    fun clampsToReservedTopZone() {
        val y = MenuVerticalPositionCalculator.clamp(
            preferredY = -50,
            menuHeight = 500,
            screenHeight = 2000,
            topReserved = 100
        )

        assertEquals(100, y)
    }

    @Test
    fun menuTallerThanAvailableSpaceStaysBelowReservedTop() {
        val y = MenuVerticalPositionCalculator.clamp(
            preferredY = 900,
            menuHeight = 1950,
            screenHeight = 2000,
            topReserved = 100
        )

        assertEquals(100, y)
    }

    @Test
    fun menuTallerThanScreenStaysBelowReservedTop() {
        val y = MenuVerticalPositionCalculator.clamp(
            preferredY = 300,
            menuHeight = 2400,
            screenHeight = 2000,
            topReserved = 100
        )

        assertEquals(100, y)
    }

    @Test
    fun zeroHeightDoesNotProduceNegativePosition() {
        val y = MenuVerticalPositionCalculator.clamp(
            preferredY = 0,
            menuHeight = 0,
            screenHeight = 2000,
            topReserved = 0
        )

        assertEquals(0, y)
    }

    @Test
    fun negativeInputsAreTreatedAsZero() {
        val y = MenuVerticalPositionCalculator.clamp(
            preferredY = 500,
            menuHeight = -100,
            screenHeight = -50,
            topReserved = -20
        )

        assertEquals(0, y)
    }
}
