package com.enaboapps.switchify.service.techniques.nodes

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSelectionStrategyTest {
    @After
    fun tearDown() {
        NodeSelectionStrategy.setRuntimeStateProviderForTesting(null)
    }

    @Test
    fun ordinaryClickableNodePrefersAccessibilityClick() {
        val decision = NodeSelectionStrategy.decide(clickableCapabilities())

        assertTrue(decision.preferAccessibilityClick)
        assertEquals(NodeSelectionReason.AccessibilityClickPreferred, decision.reason)
    }

    @Test
    fun keyboardNodePrefersTap() {
        val decision = NodeSelectionStrategy.decide(keyboardCapabilities())

        assertFalse(decision.preferAccessibilityClick)
        assertEquals(NodeSelectionReason.KeyboardNodeTap, decision.reason)
    }

    @Test
    fun repeatEnabledPrefersTap() {
        NodeSelectionStrategy.setRuntimeStateProviderForTesting {
            NodeSelectionRuntimeState(isGestureRepeatEnabled = true)
        }

        val decision = NodeSelectionStrategy.decide(clickableCapabilities())

        assertFalse(decision.preferAccessibilityClick)
        assertEquals(NodeSelectionReason.GestureRepeatTap, decision.reason)
    }

    @Test
    fun repeatSessionActivePrefersTap() {
        NodeSelectionStrategy.setRuntimeStateProviderForTesting {
            NodeSelectionRuntimeState(isGestureRepeatSessionActive = true)
        }

        val decision = NodeSelectionStrategy.decide(clickableCapabilities())

        assertFalse(decision.preferAccessibilityClick)
        assertEquals(NodeSelectionReason.GestureRepeatTap, decision.reason)
    }

    @Test
    fun gestureLockWaitingPrefersTap() {
        NodeSelectionStrategy.setRuntimeStateProviderForTesting {
            NodeSelectionRuntimeState(isGestureLockEnabled = true)
        }

        val decision = NodeSelectionStrategy.decide(clickableCapabilities())

        assertFalse(decision.preferAccessibilityClick)
        assertEquals(NodeSelectionReason.GestureLockTap, decision.reason)
    }

    @Test
    fun gestureLockEngagedPrefersTap() {
        NodeSelectionStrategy.setRuntimeStateProviderForTesting {
            NodeSelectionRuntimeState(isGestureLockEngaged = true)
        }

        val decision = NodeSelectionStrategy.decide(clickableCapabilities())

        assertFalse(decision.preferAccessibilityClick)
        assertEquals(NodeSelectionReason.GestureLockTap, decision.reason)
    }

    @Test
    fun nodeWithoutClickActionPrefersTap() {
        val decision = NodeSelectionStrategy.decide(nonClickableCapabilities())

        assertFalse(decision.preferAccessibilityClick)
        assertEquals(NodeSelectionReason.NoClickActionTap, decision.reason)
    }

    @Test
    fun runtimeStateIsReadAtDecisionTime() {
        var repeatActive = false
        NodeSelectionStrategy.setRuntimeStateProviderForTesting {
            NodeSelectionRuntimeState(isGestureRepeatEnabled = repeatActive)
        }

        val firstDecision = NodeSelectionStrategy.decide(clickableCapabilities())
        repeatActive = true
        val secondDecision = NodeSelectionStrategy.decide(clickableCapabilities())

        assertTrue(firstDecision.preferAccessibilityClick)
        assertEquals(NodeSelectionReason.AccessibilityClickPreferred, firstDecision.reason)
        assertFalse(secondDecision.preferAccessibilityClick)
        assertEquals(NodeSelectionReason.GestureRepeatTap, secondDecision.reason)
    }

    private fun clickableCapabilities(): NodeCapabilities {
        return NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(actionIds = setOf(NodeCapabilityClassifier.ACTION_ID_CLICK))
        )
    }

    private fun keyboardCapabilities(): NodeCapabilities {
        return NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(
                isKeyboardNode = true,
                actionIds = setOf(NodeCapabilityClassifier.ACTION_ID_CLICK)
            )
        )
    }

    private fun nonClickableCapabilities(): NodeCapabilities {
        return NodeCapabilityClassifier.classifyFacts(RawNodeCapabilityFacts(hasText = true))
    }
}
