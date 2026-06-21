package com.enaboapps.switchify.service.switches.external

import com.enaboapps.switchify.switches.SwitchAction
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSwitchLongPressHandlerTest {
    @After
    fun tearDown() {
        ExternalSwitchLongPressHandler.cancelLongPress()
    }

    @Test
    fun cancelLongPressClearsPendingActionWithoutPerforming() {
        ExternalSwitchLongPressHandler.setPendingActionForTesting(
            SwitchAction(SwitchAction.ACTION_TOGGLE_GESTURE_LOCK)
        )

        assertTrue(ExternalSwitchLongPressHandler.isLongPressActive())
        assertTrue(ExternalSwitchLongPressHandler.cancelLongPress())

        assertNull(ExternalSwitchLongPressHandler.getPendingAction())
        assertFalse(ExternalSwitchLongPressHandler.isLongPressActive())
    }

    @Test
    fun cancelLongPressReturnsFalseWhenThereIsNoState() {
        assertFalse(ExternalSwitchLongPressHandler.cancelLongPress())
    }
}
