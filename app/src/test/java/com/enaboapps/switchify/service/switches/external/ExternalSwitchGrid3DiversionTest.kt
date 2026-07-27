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

        assertTrue(diversion.onPressed(42, 1_000L, 1_000L) {
            normalCalls++
            false
        })
        assertTrue(diversion.onReleased(42, 1_000L, 1_100L, cancelled = false) {
            normalCalls++
            false
        })

        assertEquals(
            listOf("down:42:1000:1000", "up:42:1000:1100:false"),
            handler.calls
        )
        assertEquals(0, normalCalls)
    }

    @Test
    fun inactiveOrMissingForwarderPreservesNormalHandling() {
        val inactive = ExternalSwitchGrid3Diversion { FakeHandler(consume = false) }
        val missing = ExternalSwitchGrid3Diversion { null }

        assertFalse(inactive.onPressed(1, 10L, 10L) { false })
        assertTrue(missing.onReleased(1, 10L, 20L, cancelled = false) { true })
    }

    @Test
    fun forwardsAndroidPressSequenceMetadata() {
        val handler = FakeHandler(consume = true)
        val diversion = ExternalSwitchGrid3Diversion { handler }

        assertTrue(diversion.onPressed(42, 1_000L, 1_100L) { false })
        assertTrue(diversion.onReleased(42, 1_000L, 1_200L, cancelled = true) { false })

        assertEquals(
            listOf(
                "down:42:1000:1100",
                "up:42:1000:1200:true"
            ),
            handler.calls
        )
    }

    @Test
    fun activationPressIsSuppressedUntilItsMatchingRelease() {
        val handler = FakeHandler(consume = false)
        val diversion = ExternalSwitchGrid3Diversion { handler }
        var suppressedReleases = 0

        assertTrue(diversion.onPressed(42, 1_000L, 1_000L) { true })
        handler.activation++
        assertTrue(diversion.onPressed(42, 1_000L, 1_100L) { false })
        assertTrue(
            diversion.onReleased(
                42,
                1_000L,
                1_200L,
                cancelled = false,
                suppressedReleaseHandling = {
                    suppressedReleases++
                    true
                }
            ) { false }
        )

        assertEquals(listOf("down:42:1000:1000"), handler.calls)
        assertEquals(1, suppressedReleases)
    }

    @Test
    fun activationReleaseRemainsSuppressedAfterForwardingStops() {
        val handler = FakeHandler(consume = false)
        val diversion = ExternalSwitchGrid3Diversion { handler }
        var suppressedReleases = 0

        assertTrue(diversion.onPressed(42, 1_000L, 1_000L) { true })
        handler.activation++
        assertTrue(
            diversion.onReleased(
                42,
                1_000L,
                1_200L,
                cancelled = false,
                suppressedReleaseHandling = {
                    suppressedReleases++
                    true
                }
            ) { false }
        )

        assertEquals(1, suppressedReleases)
        assertEquals(listOf("down:42:1000:1000"), handler.calls)
    }

    private class FakeHandler(private val consume: Boolean) : Grid3SwitchInputHandler {
        val calls = mutableListOf<String>()
        var activation = 0L

        override val forwardingActivation: Long
            get() = activation

        override fun onSwitchPressed(keyCode: Int, downTimeMs: Long, eventTimeMs: Long): Boolean {
            calls += "down:$keyCode:$downTimeMs:$eventTimeMs"
            return consume
        }

        override fun onSwitchReleased(
            keyCode: Int,
            downTimeMs: Long,
            eventTimeMs: Long,
            cancelled: Boolean
        ): Boolean {
            calls += "up:$keyCode:$downTimeMs:$eventTimeMs:$cancelled"
            return consume
        }
    }
}
