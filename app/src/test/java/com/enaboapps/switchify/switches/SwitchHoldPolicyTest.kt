package com.enaboapps.switchify.switches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchHoldPolicyTest {
    private val event = SwitchEvent(
        name = "Primary",
        code = "1",
        pressAction = SwitchAction(SwitchAction.ACTION_SELECT),
        holdActions = listOf(
            SwitchAction(SwitchAction.ACTION_MOVE_TO_NEXT_ITEM),
            SwitchAction(SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM)
        )
    )

    @Test
    fun enabledHoldActionsRemainEffective() {
        assertEquals(event.holdActions, SwitchHoldPolicy.effectiveHoldActions(event, true))
    }

    @Test
    fun disabledHoldActionsAreHiddenWithoutChangingStoredActions() {
        val effective = SwitchHoldPolicy.effectiveHoldActions(event, false)

        assertTrue(effective.isEmpty())
        assertEquals(2, event.holdActions.size)
    }

    @Test
    fun configuredActionsExcludeDisabledHoldActions() {
        val configured = SwitchHoldPolicy.configuredActionIds(listOf(event), false)

        assertEquals(setOf(SwitchAction.ACTION_SELECT), configured)
    }
}
