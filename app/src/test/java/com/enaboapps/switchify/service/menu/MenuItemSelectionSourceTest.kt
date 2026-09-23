package com.enaboapps.switchify.service.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuItemSelectionSourceTest {
    @Test
    fun scanningOnlyItemRejectsTouchSelection() {
        var actions = 0
        var rejections = 0
        val item = MenuItem(
            id = "activate",
            descriptionResource = null,
            closeOnSelect = false,
            requiresScanningSelection = true,
            onRejectedTouchSelection = { rejections += 1 },
            action = { actions += 1 }
        )

        item.select()

        assertEquals(0, actions)
        assertEquals(1, rejections)
    }

    @Test
    fun scanningOnlyItemAcceptsScanningSelection() {
        var actions = 0
        val item = MenuItem(
            id = "activate",
            descriptionResource = null,
            closeOnSelect = false,
            requiresScanningSelection = true,
            action = { actions += 1 }
        )

        item.select(MenuSelectionSource.SCANNING)

        assertEquals(1, actions)
    }

    @Test
    fun normalItemStillAcceptsTouchSelection() {
        var actions = 0
        val item = MenuItem(
            id = "cancel",
            descriptionResource = null,
            closeOnSelect = false,
            action = { actions += 1 }
        )

        item.select()

        assertEquals(1, actions)
    }
}
