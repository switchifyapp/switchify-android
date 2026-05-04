package com.enaboapps.switchify.service.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardStateMachineTest {
    @Test
    fun validReturnFlowTransitionsBackToScanning() {
        val stateMachine = KeyboardStateMachine()

        assertEquals(KeyboardScanState.SCANNING, stateMachine.transition(KeyboardEvent.KeyboardShown))
        assertEquals(KeyboardScanState.ESCAPED, stateMachine.transition(KeyboardEvent.EscapeRequested))
        assertEquals(KeyboardScanState.RETURNING, stateMachine.transition(KeyboardEvent.ReturnRequested))
        assertEquals(KeyboardScanState.SCANNING, stateMachine.transition(KeyboardEvent.ReturnCompleted))
        assertEquals(KeyboardScanState.SCANNING, stateMachine.getCurrentState())
    }

    @Test
    fun keyboardHiddenTransitionsToHiddenFromActiveStates() {
        val scanningStateMachine = KeyboardStateMachine()
        scanningStateMachine.transition(KeyboardEvent.KeyboardShown)
        assertEquals(KeyboardScanState.HIDDEN, scanningStateMachine.transition(KeyboardEvent.KeyboardHidden))

        val escapedStateMachine = KeyboardStateMachine()
        escapedStateMachine.transition(KeyboardEvent.KeyboardShown)
        escapedStateMachine.transition(KeyboardEvent.EscapeRequested)
        assertEquals(KeyboardScanState.HIDDEN, escapedStateMachine.transition(KeyboardEvent.KeyboardHidden))

        val returningStateMachine = KeyboardStateMachine()
        returningStateMachine.transition(KeyboardEvent.KeyboardShown)
        returningStateMachine.transition(KeyboardEvent.EscapeRequested)
        returningStateMachine.transition(KeyboardEvent.ReturnRequested)
        assertEquals(KeyboardScanState.HIDDEN, returningStateMachine.transition(KeyboardEvent.KeyboardHidden))
    }

    @Test
    fun invalidTransitionsReturnNullAndKeepCurrentState() {
        val stateMachine = KeyboardStateMachine()

        assertNull(stateMachine.transition(KeyboardEvent.EscapeRequested))
        assertEquals(KeyboardScanState.HIDDEN, stateMachine.getCurrentState())

        stateMachine.transition(KeyboardEvent.KeyboardShown)
        assertNull(stateMachine.transition(KeyboardEvent.ReturnRequested))
        assertEquals(KeyboardScanState.SCANNING, stateMachine.getCurrentState())

        stateMachine.transition(KeyboardEvent.EscapeRequested)
        assertNull(stateMachine.transition(KeyboardEvent.ReturnCompleted))
        assertEquals(KeyboardScanState.ESCAPED, stateMachine.getCurrentState())
    }
}
