package com.enaboapps.switchify.switches.profiles

import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchProfileValidatorTest {
    @Test
    fun autoScanRequiresSelect() {
        val invalid = SwitchProfileValidator.validate(
            listOf(event("next", SwitchAction.ACTION_MOVE_TO_NEXT_ITEM)),
            setOf(SwitchAction.ACTION_SELECT),
            setOf(SwitchAction.ACTION_SELECT, SwitchAction.ACTION_MOVE_TO_NEXT_ITEM),
            true
        )

        assertFalse(invalid.isValid)
        assertEquals(setOf(SwitchAction.ACTION_SELECT), invalid.missingActionIds)
    }

    @Test
    fun manualScanRequiresSelectNextAndPreviousAcrossPressAndHold() {
        val result = SwitchProfileValidator.validate(
            listOf(
                event(
                    "select",
                    SwitchAction.ACTION_SELECT,
                    SwitchAction.ACTION_MOVE_TO_NEXT_ITEM
                ),
                event("previous", SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM)
            ),
            setOf(
                SwitchAction.ACTION_SELECT,
                SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
                SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM
            ),
            setOf(
                SwitchAction.ACTION_SELECT,
                SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
                SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM
            ),
            true
        )

        assertTrue(result.isValid)
    }

    @Test
    fun unsupportedActionsAreRejected() {
        val result = SwitchProfileValidator.validate(
            listOf(event("unsupported", 999)),
            emptySet(),
            setOf(SwitchAction.ACTION_SELECT),
            true
        )

        assertFalse(result.isValid)
        assertEquals(setOf(999), result.unsupportedActionIds)
    }

    @Test
    fun disabledHoldActionsDoNotSatisfyRequirements() {
        val result = SwitchProfileValidator.validate(
            listOf(event("select", SwitchAction.ACTION_NONE, SwitchAction.ACTION_SELECT)),
            setOf(SwitchAction.ACTION_SELECT),
            setOf(SwitchAction.ACTION_NONE, SwitchAction.ACTION_SELECT),
            false
        )

        assertFalse(result.isValid)
        assertEquals(setOf(SwitchAction.ACTION_SELECT), result.missingActionIds)
    }

    @Test
    fun disabledUnsupportedHoldActionsAreIgnored() {
        val result = SwitchProfileValidator.validate(
            listOf(event("select", SwitchAction.ACTION_SELECT, 999)),
            setOf(SwitchAction.ACTION_SELECT),
            setOf(SwitchAction.ACTION_SELECT),
            false
        )

        assertTrue(result.isValid)
        assertTrue(result.unsupportedActionIds.isEmpty())
    }

    private fun event(code: String, pressAction: Int, vararg holdActions: Int) = SwitchEvent(
        name = code,
        code = code,
        pressAction = SwitchAction(pressAction),
        holdActions = holdActions.map(::SwitchAction)
    )
}
