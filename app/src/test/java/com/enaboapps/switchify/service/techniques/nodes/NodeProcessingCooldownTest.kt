package com.enaboapps.switchify.service.techniques.nodes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeProcessingCooldownTest {
    @Test
    fun monotonicClockMayHaveNegativeOrigin() {
        val cooldown = NodeProcessingCooldown { -100_000_000_000L }
        assertTrue(cooldown.canProcess("app"))
        repeat(5) { cooldown.failure("app") }
        assertFalse(cooldown.canProcess("app"))
        cooldown.reset()
        assertTrue(cooldown.canProcess("app"))
    }

    @Test
    fun repeatedRejectedTreesTriggerCooldownUntilDeadline() {
        var now = 0L
        val cooldown = NodeProcessingCooldown { now }
        repeat(5) {
            assertTrue(cooldown.canProcess("app"))
            cooldown.failure("app")
        }
        assertFalse(cooldown.canProcess("app"))
        now = 10_000_000_000L
        assertTrue(cooldown.canProcess("app"))
    }

    @Test
    fun anotherWindowAndCleanupResetFailures() {
        val cooldown = NodeProcessingCooldown { 0L }
        cooldown.canProcess("app")
        repeat(5) { cooldown.failure("app") }
        assertTrue(cooldown.canProcess("keyboard"))
        repeat(5) { cooldown.failure("keyboard") }
        cooldown.reset()
        assertTrue(cooldown.canProcess("keyboard"))
    }

    @Test
    fun successfulProcessingBreaksFailureSequence() {
        val cooldown = NodeProcessingCooldown { 0L }
        cooldown.canProcess("app")
        repeat(4) { cooldown.failure("app") }
        cooldown.success()
        cooldown.failure("app")
        assertTrue(cooldown.canProcess("app"))
    }
}
