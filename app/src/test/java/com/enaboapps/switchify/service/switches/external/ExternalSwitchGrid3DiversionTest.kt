package com.enaboapps.switchify.service.switches.external

import com.enaboapps.switchify.service.grid3.Grid3SwitchInputHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSwitchGrid3DiversionTest {
    @Test
    fun activeForwarderConsumesPressAndReleaseBeforeNormalHandling() {
        val handler = FakeHandler(consume = true)
        val diversion = ExternalSwitchGrid3Diversion { handler }
        var normalCalls = 0

        assertTrue(diversion.onPressed(42) {
            normalCalls++
            false
        })
        assertTrue(diversion.onReleased(42) {
            normalCalls++
            false
        })

        assertEquals(listOf("down:42", "up:42"), handler.calls)
        assertEquals(0, normalCalls)
    }

    @Test
    fun inactiveOrMissingForwarderPreservesNormalHandling() {
        val inactive = ExternalSwitchGrid3Diversion { FakeHandler(consume = false) }
        val missing = ExternalSwitchGrid3Diversion { null }

        assertFalse(inactive.onPressed(1) { false })
        assertTrue(missing.onReleased(1) { true })
    }

    private class FakeHandler(private val consume: Boolean) : Grid3SwitchInputHandler {
        val calls = mutableListOf<String>()

        override fun onSwitchPressed(keyCode: Int): Boolean {
            calls += "down:$keyCode"
            return consume
        }

        override fun onSwitchReleased(keyCode: Int): Boolean {
            calls += "up:$keyCode"
            return consume
        }
    }
}
