package com.enaboapps.switchify.service.techniques.nodes

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedNodeTraversalTest {
    @Test
    fun wideTreeCannotFetchOrRetainMoreThanBudget() {
        var fetched = 0
        assertThrows(NodeTraversalLimitException::class.java) {
            traverseNodes(0, NodeProcessingBudget(), { if (it == 0) 50_000 else 0 }, { _, index ->
                fetched++
                index + 1
            })
        }
        assertEquals(999, fetched)
    }

    @Test
    fun missingChildrenStillConsumeFetchBudget() {
        var fetched = 0
        assertThrows(NodeTraversalLimitException::class.java) {
            traverseNodes(0, NodeProcessingBudget(maxChildFetches = 8), { 50_000 }, { _, _ ->
                fetched++
                null
            })
        }
        assertEquals(8, fetched)
    }

    @Test
    fun deepTreeStopsBeforeFetchingBeyondDepthLimit() {
        var fetched = 0
        assertThrows(NodeTraversalLimitException::class.java) {
            traverseNodes(0, NodeProcessingBudget(maxDepth = 8), { 1 }, { node, _ ->
                fetched++
                node + 1
            })
        }
        assertEquals(8, fetched)
    }

    @Test
    fun pathsAndOrderMatchBreadthFirstTraversal() {
        val result = traverseNodes("", NodeProcessingBudget(), { if (it.length < 2) 2 else 0 },
            { node, index -> node + index })
        assertEquals(listOf("", "0", "1", "00", "01", "10", "11"), result.map { it.node })
        assertEquals(listOf(1, 0), result[5].path)
        assertEquals(listOf(5, 6), result[2].children)
    }

    @Test
    fun deadlineStopsSynchronousTraversal() {
        var time = 0L
        var fetched = 0
        val budget = NodeProcessingBudget(timeoutNanos = 10, clock = { time })
        assertThrows(NodeTraversalLimitException::class.java) {
            traverseNodes(0, budget, { 100 }, { _, index ->
                fetched++
                time += 10
                index + 1
            })
        }
        assertEquals(1, fetched)
    }

    @Test
    fun cancellationIsNotConvertedToTraversalFailure() {
        var cancelled = false
        val budget = NodeProcessingBudget(checkCancellation = {
            if (cancelled) throw CancellationException()
        })
        assertThrows(CancellationException::class.java) {
            traverseNodes(0, budget, { 10 }, { _, index ->
                cancelled = true
                index + 1
            })
        }
    }

    @Test
    fun textHasPerLabelAndPerPassLimits() {
        val budget = NodeProcessingBudget()
        val huge = "x".repeat(100_000)
        assertEquals(1024, budget.text(huge).length)
        val total = (1..1000).sumOf { budget.text(huge).length } + 1024
        assertEquals(65_536, total)
    }

    @Test
    fun exactNodeLimitIsAcceptedForCompleteTree() {
        val result = traverseNodes(0, NodeProcessingBudget(), { if (it == 0) 999 else 0 },
            { _, index -> index + 1 })
        assertEquals(1000, result.size)
        assertTrue(result.last().children.isEmpty())
    }

    @Test
    fun oversizedIdentityTextIsOmittedRatherThanTruncatedIntoFalseMatch() {
        val budget = NodeProcessingBudget()
        assertNull(budget.identityText("x".repeat(4097)))
        assertEquals("Open", budget.identityText("Open"))
        repeat(16) { budget.identityText("x".repeat(4096)) }
        assertNull(budget.identityText("x".repeat(4096)))
    }
}
