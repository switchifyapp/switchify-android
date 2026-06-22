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
        ExternalSwitchLongPressHandler.cancel()
    }

    @Test
    fun cancelClearsPendingActionWithoutPerforming() {
        ExternalSwitchLongPressHandler.setPendingActionForTesting(
            SwitchAction(SwitchAction.ACTION_TOGGLE_GESTURE_LOCK)
        )

        assertTrue(ExternalSwitchLongPressHandler.isActive())
        assertTrue(ExternalSwitchLongPressHandler.cancel())

        assertNull(ExternalSwitchLongPressHandler.getPendingAction())
        assertFalse(ExternalSwitchLongPressHandler.isActive())
    }

    @Test
    fun cancelReturnsFalseWhenThereIsNoState() {
        assertFalse(ExternalSwitchLongPressHandler.cancel())
    }

    @Test
    fun cancelClearsActiveHoldPickerState() {
        ExternalSwitchLongPressHandler.setHoldActionsForTesting(
            listOf(SwitchAction(SwitchAction.ACTION_TOGGLE_GESTURE_LOCK))
        )

        assertTrue(ExternalSwitchLongPressHandler.isActive())
        assertTrue(ExternalSwitchLongPressHandler.cancel())

        assertFalse(ExternalSwitchLongPressHandler.isActive())
        assertNull(ExternalSwitchLongPressHandler.getPendingAction())
    }

    @Test
    fun cancelPreventsPendingHoldActionFromBeingPerformed() {
        ExternalSwitchLongPressHandler.setPendingActionForTesting(
            SwitchAction(SwitchAction.ACTION_TOGGLE_GESTURE_LOCK)
        )

        assertTrue(ExternalSwitchLongPressHandler.cancel())

        assertFalse(ExternalSwitchLongPressHandler.stopAndPerformPending(null))
    }
}
