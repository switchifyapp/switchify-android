package com.enaboapps.switchify.service.techniques.nodes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSelectionPerformerTest {
    @Test
    fun performDoesNotFallbackWhenAccessibilityClickSucceeds() {
        var fallbackCount = 0

        val result = NodeSelectionPerformer.perform(
            accessibilityClick = { true },
            fallbackTap = { fallbackCount++ }
        )

        assertTrue(result)
        assertEquals(0, fallbackCount)
    }

    @Test
    fun performFallsBackWhenAccessibilityClickReturnsFalse() {
        var fallbackCount = 0

        val result = NodeSelectionPerformer.perform(
            accessibilityClick = { false },
            fallbackTap = { fallbackCount++ }
        )

        assertFalse(result)
        assertEquals(1, fallbackCount)
    }

    @Test
    fun performFallsBackWhenAccessibilityClickThrows() {
        var fallbackCount = 0

        val result = NodeSelectionPerformer.perform(
            accessibilityClick = { throw IllegalStateException("click failed") },
            fallbackTap = { fallbackCount++ }
        )

        assertFalse(result)
        assertEquals(1, fallbackCount)
    }

    @Test
    fun performRunsFallbackOnlyOnce() {
        var fallbackCount = 0

        NodeSelectionPerformer.perform(
            accessibilityClick = { false },
            fallbackTap = { fallbackCount++ }
        )

        assertEquals(1, fallbackCount)
    }

    @Test
    fun performFallsBackWithoutClickWhenAccessibilityClickIsNotPreferred() {
        var clickCount = 0
        var fallbackCount = 0

        val result = NodeSelectionPerformer.perform(
            accessibilityClick = {
                clickCount++
                true
            },
            fallbackTap = { fallbackCount++ },
            preferAccessibilityClick = false
        )

        assertFalse(result)
        assertEquals(0, clickCount)
        assertEquals(1, fallbackCount)
    }
}
