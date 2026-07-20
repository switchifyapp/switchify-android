package com.enaboapps.switchify.service.menu.structure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldAndDragMenuDefinitionTest {
    @Test
    fun holdAndDragAppearsImmediatelyAfterDrag() {
        val ids = MenuItemRegistry.getGesturesMenuDefinitions().map { it.id }
        val dragIndex = ids.indexOf(MenuConstants.ItemIds.Gestures.DRAG)

        assertTrue(dragIndex >= 0)
        assertEquals(
            MenuConstants.ItemIds.Gestures.HOLD_AND_DRAG,
            ids[dragIndex + 1]
        )
    }
}
