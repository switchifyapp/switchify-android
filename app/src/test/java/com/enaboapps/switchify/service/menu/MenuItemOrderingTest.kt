package com.enaboapps.switchify.service.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuItemOrderingTest {
    @Test
    fun contextualItemsStayAheadOfConfiguredItems() {
        assertEquals(
            listOf("actions", "home", "back"),
            MenuItemOrdering.withLeadingItems(
                leadingItems = listOf("actions"),
                orderedItems = listOf("home", "back")
            )
        )
    }

    @Test
    fun noContextualItemsLeaveConfiguredOrderUntouched() {
        assertEquals(
            listOf("home", "back"),
            MenuItemOrdering.withLeadingItems(
                leadingItems = emptyList(),
                orderedItems = listOf("home", "back")
            )
        )
    }
}
