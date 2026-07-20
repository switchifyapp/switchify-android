package com.enaboapps.switchify.service.gestures.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSequenceTerminalTest {
    @Test
    fun completionRunsOnlyOnce() {
        var completions = 0
        val terminal = terminal(onCompleted = { completions++ })

        assertTrue(terminal.complete())
        assertFalse(terminal.complete())
        assertFalse(terminal.cancel())
        assertEquals(1, completions)
    }

    @Test
    fun cancellationPreventsLaterCompletion() {
        var cancellations = 0
        var completions = 0
        val terminal = terminal(
            onCompleted = { completions++ },
            onCancelled = { cancellations++ }
        )

        assertTrue(terminal.cancel())
        assertFalse(terminal.complete())
        assertEquals(1, cancellations)
        assertEquals(0, completions)
    }

    @Test
    fun errorPreventsOtherTerminalCallbacks() {
        var errors = 0
        val terminal = terminal(onError = { errors++ })

        assertTrue(terminal.error(IllegalStateException()))
        assertFalse(terminal.cancel())
        assertEquals(1, errors)
    }

    private fun terminal(
        onCompleted: () -> Unit = {},
        onCancelled: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) = GestureSequenceTerminal(onCompleted, onCancelled, onError)
}
