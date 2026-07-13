package com.enaboapps.switchify.service.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuContentWidthCalculatorTest {
    @Test
    fun usesTheWidestRowWithoutFillingAvailableWidth() {
        val width = MenuContentWidthCalculator.calculate(
            maxWidthPx = 900,
            minimumWidthPx = 0,
            rows = listOf(
                MenuRowWidth(labelWidthPx = 120, fixedWidthPx = 160),
                MenuRowWidth(labelWidthPx = 240, fixedWidthPx = 160),
                MenuRowWidth(labelWidthPx = 180, fixedWidthPx = 160)
            )
        )

        assertEquals(400, width)
    }

    @Test
    fun includesAdditionalFixedWidthForChevronRows() {
        val width = MenuContentWidthCalculator.calculate(
            maxWidthPx = 900,
            minimumWidthPx = 0,
            rows = listOf(
                MenuRowWidth(labelWidthPx = 200, fixedWidthPx = 160),
                MenuRowWidth(labelWidthPx = 200, fixedWidthPx = 184)
            )
        )

        assertEquals(384, width)
    }

    @Test
    fun clampsLongLabelsToTheSafeMaximum() {
        val width = MenuContentWidthCalculator.calculate(
            maxWidthPx = 500,
            minimumWidthPx = 0,
            rows = listOf(MenuRowWidth(labelWidthPx = 700, fixedWidthPx = 160))
        )

        assertEquals(500, width)
    }

    @Test
    fun emptyPageDoesNotReserveContentWidth() {
        assertEquals(
            0,
            MenuContentWidthCalculator.calculate(
                maxWidthPx = 500,
                minimumWidthPx = 0,
                rows = emptyList()
            )
        )
    }

    @Test
    fun invalidMeasurementsCannotCreateNegativeWidth() {
        val width = MenuContentWidthCalculator.calculate(
            maxWidthPx = 500,
            minimumWidthPx = 0,
            rows = listOf(MenuRowWidth(labelWidthPx = -20, fixedWidthPx = -10))
        )

        assertEquals(0, width)
    }

    @Test
    fun usesExistingTitleOrNavigationWidthWithoutGrowingTheSurface() {
        val width = MenuContentWidthCalculator.calculate(
            maxWidthPx = 900,
            minimumWidthPx = 420,
            rows = listOf(MenuRowWidth(labelWidthPx = 120, fixedWidthPx = 160))
        )

        assertEquals(420, width)
    }

    @Test
    fun minimumWidthStillRespectsTheSafeMaximum() {
        val width = MenuContentWidthCalculator.calculate(
            maxWidthPx = 500,
            minimumWidthPx = 700,
            rows = emptyList()
        )

        assertEquals(500, width)
    }

    @Test
    fun navigationCellsKeepTheirPreferredWidthWhenItFits() {
        assertEquals(
            140,
            MenuContentWidthCalculator.navigationCellWidth(
                availableWidthPx = 600,
                preferredWidthPx = 140,
                minimumTouchWidthPx = 96,
                itemCount = 3
            )
        )
    }

    @Test
    fun navigationCellsShrinkTogetherToFitTheAvailableWidth() {
        assertEquals(
            100,
            MenuContentWidthCalculator.navigationCellWidth(
                availableWidthPx = 300,
                preferredWidthPx = 180,
                minimumTouchWidthPx = 96,
                itemCount = 3
            )
        )
    }

    @Test
    fun navigationCellsRetainMinimumTouchWidthWhenSpaceAllows() {
        assertEquals(
            96,
            MenuContentWidthCalculator.navigationCellWidth(
                availableWidthPx = 360,
                preferredWidthPx = 80,
                minimumTouchWidthPx = 96,
                itemCount = 3
            )
        )
    }

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
