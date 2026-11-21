package com.enaboapps.switchify.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeoutException

class TrackedOperationsTest {

    private class FakeOperation {
        var complete = false
        var failure: Exception? = null
        val lateCallbacks = mutableListOf<() -> Unit>()
        var completesWhileTimingOut = false

        fun finish(error: Exception? = null) {
            complete = true
            failure = error
            lateCallbacks.forEach { it() }
        }
    }

    private val started = mutableListOf<FakeOperation>()
    private var lateCompletions = 0

    private val operations = TrackedOperations<FakeOperation>(
        await = { operation, _ ->
            if (!operation.complete) {
                if (operation.completesWhileTimingOut) operation.complete = true
                throw TimeoutException()
            }
            operation.failure?.let { throw it }
        },
        onLateCompletion = { operation, callback -> operation.lateCallbacks += callback }
    )

    private fun run(name: String, completeOnStart: Boolean = false) {
        operations.run(name, 30, { lateCompletions++ }) {
            FakeOperation().also {
                it.complete = completeOnStart
                started += it
            }
        }
    }

    @Test
    fun aCompletedOperationIsNotReused() {
        run("unsubscribe", completeOnStart = true)
        run("unsubscribe", completeOnStart = true)

        assertEquals(2, started.size)
    }

    @Test
    fun aTimedOutOperationIsReusedInsteadOfStartedAgain() {
        assertThrows(TipOperationPendingException::class.java) { run("unsubscribe") }
        assertThrows(TipOperationPendingException::class.java) { run("unsubscribe") }

        assertEquals(1, started.size)
    }

    @Test
    fun anOperationThatSucceedsBetweenAttemptsCountsOnceAndWakesTheCaller() {
        assertThrows(TipOperationPendingException::class.java) { run("unsubscribe") }

        started.single().finish()
        assertEquals(1, lateCompletions)

        run("unsubscribe")
        assertEquals(1, started.size)

        run("unsubscribe", completeOnStart = true)
        assertEquals(2, started.size)
    }

    @Test
    fun anOperationThatFailsBetweenAttemptsReportsTheFailureOnceThenStartsAfresh() {
        assertThrows(TipOperationPendingException::class.java) { run("deleteToken") }
        started.single().finish(IOException("offline"))

        assertThrows(IOException::class.java) { run("deleteToken") }
        assertEquals(1, started.size)

        run("deleteToken", completeOnStart = true)
        assertEquals(2, started.size)
    }

    @Test
    fun theLateCompletionListenerIsAttachedOncePerOperation() {
        repeat(3) {
            assertThrows(TipOperationPendingException::class.java) { run("unsubscribe") }
        }

        assertEquals(1, started.single().lateCallbacks.size)

        started.single().finish()

        assertEquals(1, lateCompletions)
    }

    @Test
    fun anOperationThatCompletesJustAsItsWaitTimesOutIsKeptForTheNextAttempt() {
        assertThrows(TipOperationPendingException::class.java) {
            operations.run("unsubscribe", 30, { lateCompletions++ }) {
                FakeOperation().also {
                    it.completesWhileTimingOut = true
                    started += it
                }
            }
        }

        run("unsubscribe")

        assertEquals(1, started.size)
    }

    @Test
    fun aForgottenOperationNoLongerHoldsAListenerSlot() {
        assertThrows(TipOperationPendingException::class.java) { run("subscribe") }
        operations.forget("subscribe")

        assertThrows(TipOperationPendingException::class.java) { run("subscribe") }

        assertEquals(2, started.size)
        assertEquals(1, started[1].lateCallbacks.size)
    }

    @Test
    fun aForgottenOperationIsNotReused() {
        assertThrows(TipOperationPendingException::class.java) { run("subscribe") }

        operations.forget("subscribe")
        run("subscribe", completeOnStart = true)

        assertEquals(2, started.size)
    }

    @Test
    fun settlingWaitsForAPendingOperationAndIgnoresItsFailure() {
        operations.settle("deleteToken", 120) { lateCompletions++ }
        assertEquals(0, started.size)

        assertThrows(TipOperationPendingException::class.java) { run("deleteToken") }
        assertThrows(TipOperationPendingException::class.java) {
            operations.settle("deleteToken", 120) { lateCompletions++ }
        }

        started.single().finish(IOException("offline"))
        operations.settle("deleteToken", 120) { lateCompletions++ }

        run("deleteToken", completeOnStart = true)
        assertEquals(2, started.size)
    }
}
