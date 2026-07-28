package com.enaboapps.switchify.service.switches.external

import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlInputHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSwitchPcSwitchControlDiversionTest {
    @Test
    fun activeForwarderConsumesPressAndReleaseBeforeNormalHandling() {
        val handler = FakeHandler(consume = true)
        val diversion = ExternalSwitchPcSwitchControlDiversion { handler }
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
        val inactive = ExternalSwitchPcSwitchControlDiversion { FakeHandler(consume = false) }
        val missing = ExternalSwitchPcSwitchControlDiversion { null }

        assertFalse(inactive.onPressed(1, 10L, 10L) { false })
        assertTrue(missing.onReleased(1, 10L, 20L, cancelled = false) { true })
    }

    @Test
    fun unhandledNormalPressDoesNotSuppressReleaseAfterActivationChanges() {
        val handler = FakeHandler(consume = false)
        val diversion = ExternalSwitchPcSwitchControlDiversion { handler }
        var suppressedReleases = 0
        var normalReleases = 0

        assertFalse(diversion.onPressed(42, 1_000L, 1_000L) { false })
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
            ) {
                normalReleases++
                true
            }
        )

        assertEquals(0, suppressedReleases)
        assertEquals(1, normalReleases)
    }

    @Test
    fun forwardsAndroidPressSequenceMetadata() {
        val handler = FakeHandler(consume = true)
        val diversion = ExternalSwitchPcSwitchControlDiversion { handler }

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
        val diversion = ExternalSwitchPcSwitchControlDiversion { handler }
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
        val diversion = ExternalSwitchPcSwitchControlDiversion { handler }
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

    @Test
    fun resetSuppressesReleaseFromForwardedPress() {
        val handler = FakeHandler(consume = true)
        val diversion = ExternalSwitchPcSwitchControlDiversion { handler }
        var normalCalls = 0
        var suppressedReleases = 0

        assertTrue(diversion.onPressed(42, 1_000L, 1_000L) {
            normalCalls++
            false
        })
        diversion.reset()
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
            ) {
                normalCalls++
                false
            }
        )

        assertEquals(1, suppressedReleases)
        assertEquals(0, normalCalls)
        assertEquals(listOf("down:42:1000:1000"), handler.calls)
    }

    private class FakeHandler(private val consume: Boolean) : PcSwitchControlInputHandler {
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
