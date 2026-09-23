package com.enaboapps.switchify.service.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuGridLayoutTest {
    private fun layout(
        width: Int = 300,
        height: Int = 240,
        count: Int = 20,
        minimumWidth: Int = 80,
        cellHeight: Int = 80,
        fits: (Int) -> Boolean = { true }
    ) = MenuGridLayoutPolicy.calculate(width, height, count, minimumWidth, cellHeight, 52, fits)

    @Test fun columnsFollowMinimumWidthAndFitRows() {
        val grid = layout()
        assertEquals(3, grid.columns)
        assertEquals(3, grid.rows)
        assertEquals(9, grid.pageCapacity)
        assertEquals(100, grid.cellWidthPx)
    }

    @Test fun smallMenusUseOnlyNeededColumns() {
        assertEquals(1, layout(count = 1).columns)
        assertEquals(2, layout(count = 2).columns)
        assertEquals(1, layout(count = 0).columns)
    }

    @Test fun wideScreensUseUpToFiveColumns() {
        assertEquals(4, layout(width = 350).columns)
        assertEquals(5, layout(width = 400).columns)
        assertEquals(5, layout(width = 800).columns)
        assertEquals(4, layout(width = 800, count = 4).columns)
    }

    @Test fun narrowOrScaledMenusReduceColumns() {
        assertEquals(2, layout(width = 200).columns)
        assertEquals(1, layout(width = 140).columns)
        assertEquals(1, layout(minimumWidth = 160).columns)
    }

    @Test fun longLabelsReduceColumnsBeforeTruncation() {
        val testedWidths = mutableListOf<Int>()
        val grid = layout(fits = { testedWidths.add(it); it >= 150 })
        assertEquals(2, grid.columns)
        assertEquals(listOf(100, 150), testedWidths)
        assertEquals(1, layout(fits = { false }).columns)
    }

    @Test fun largeTextAndLandscapeReduceRowsWithoutReducingTouchHeight() {
        assertEquals(1, layout(cellHeight = 160).rows)
        assertEquals(1, layout(height = 100).rows)
        assertTrue(layout(height = 20).cellHeightPx >= 52)
        assertEquals(1, layout(width = 0, height = 0).pageCapacity)
    }

    @Test fun pagesPreserveOrderAndDoNotIncludeEmptyCells() {
        val items = (1..20).toList()
        val grid = layout()
        val pages = grid.pages(items)
        assertEquals(listOf(9, 9, 2), pages.map { it.size })
        assertEquals(items, pages.flatten())
        assertEquals(listOf(listOf(19, 20)), grid.rows(pages.last()))
    }

    @Test fun emptyMenuStillHasOnePage() {
        assertEquals(listOf(emptyList<Int>()), layout().pages(emptyList<Int>()))
    }

    @Test fun parentPageIsRetainedOrClampedAfterReflow() {
        assertEquals(1, layout().clampPage(1, 20))
        assertEquals(2, layout().clampPage(8, 20))
        assertEquals(0, layout().clampPage(1, 0))
        assertEquals(0, layout().clampPage(-1, 20))
    }

    @Test fun contextualContentAndFixedNavigationHaveExplicitRows() {
        val sections = MenuPageSections(listOf("Actions"), listOf("Tap", "Scroll", "Home", "Settings"),
            listOf("Back", null, "Close", "Next page"))
        assertEquals(listOf(listOf("Actions"), listOf("Tap", "Scroll", "Home"), listOf("Settings"),
            listOf("Back", "Close", "Next page")), sections.rows(3, 4))
        assertEquals(listOf(listOf("Back"), listOf("Close", "Next page")), sections.rows(3, 2).takeLast(2))
    }

    @Test fun removingContextReclaimsRowWithoutChangingContentOrder() {
        val content = listOf("Tap", "Home", "Settings")
        val withContext = MenuPageSections(listOf("Actions"), content, emptyList()).rows(3, 4)
        val withoutContext = MenuPageSections(emptyList(), content, emptyList()).rows(3, 4)
        assertEquals(withContext.drop(1), withoutContext)
    }

    @Test fun restrictedMenusHaveNoNavigationOrEmptyScanRows() {
        val rows = MenuPageSections(emptyList(), listOf("Activate", "Cancel"), emptyList()).rows(2, 4)
        assertEquals(listOf(listOf("Activate", "Cancel")), rows)
        assertTrue(MenuPageSections<String>(emptyList(), emptyList(), listOf(null, null)).rows(3, 2).isEmpty())
    }

    @Test fun dismissedLayoutAndOldPageGenerationsAreRejected() {
        val state = MenuRenderGeneration()
        state.open()
        val firstPage = state.current
        assertTrue(state.isCurrent(firstPage))
        state.next()
        assertFalse(state.isCurrent(firstPage))
        val secondPage = state.current
        state.close()
        assertFalse(state.isCurrent(secondPage))
        state.open()
        assertFalse(state.isCurrent(firstPage))
        assertFalse(state.isCurrent(secondPage))
        assertTrue(state.isCurrent(state.current))
    }
}
