package com.enaboapps.switchify.service.techniques.nodes

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeExaminerTest {
    @Test
    fun cancellationExceptionIsExpectedNodeExaminerCancellation() {
        val error = CancellationException("cancelled")

        assertTrue(NodeExaminer.isExpectedNodeExaminerCancellation(error))
    }

    @Test
    fun jobCancellationExceptionIsExpectedNodeExaminerCancellation() {
        val error = CancellationException("StandaloneCoroutine was cancelled")

        assertTrue(NodeExaminer.isExpectedNodeExaminerCancellation(error))
    }

    @Test
    fun ordinaryExceptionIsNotExpectedNodeExaminerCancellation() {
        val error = IllegalStateException("failed")

        assertFalse(NodeExaminer.isExpectedNodeExaminerCancellation(error))
    }

    @Test
    fun visibleKeyboardUsesKeyboardRoot() {
        assertTrue(
            NodeExaminer.shouldExamineKeyboardRoot(
                isKeyboardVisible = true,
                isEscapedFromKeyboard = false
            )
        )
    }

    @Test
    fun escapedKeyboardUsesApplicationRoot() {
        assertFalse(
            NodeExaminer.shouldExamineKeyboardRoot(
                isKeyboardVisible = true,
                isEscapedFromKeyboard = true
            )
        )
        assertTrue(
            NodeExaminer.shouldExamineApplicationRoot(
                isKeyboardVisible = true,
                isEscapedFromKeyboard = true
            )
        )
    }

    @Test
    fun hiddenKeyboardUsesActiveWindowRoot() {
        assertFalse(
            NodeExaminer.shouldExamineKeyboardRoot(
                isKeyboardVisible = false,
                isEscapedFromKeyboard = false
            )
        )
        assertFalse(
            NodeExaminer.shouldExamineApplicationRoot(
                isKeyboardVisible = false,
                isEscapedFromKeyboard = false
            )
        )
    }
}
