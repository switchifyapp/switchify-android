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
}
