package com.enaboapps.switchify.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TipRegistrationReconcilerTest {

    private class FakeState : TipRegistrationState {
        override var enabled = false
        override var registrationMayExist = false
        override var subscribedAtMillis = 0L
        override var subscribedVersionCode = 0
        override var removalStep = 0
    }

    private class FakeClient : TipRegistrationClient {
        val allCalls = mutableListOf<String>()
        val calls get() = allCalls.filter { it != "finishPendingDeletion" }
        val failing = mutableSetOf<String>()
        val pending = mutableSetOf<String>()

        private val inFlight = mutableSetOf<String>()

        private fun call(name: String) {
            allCalls += name
            if (name in pending) {
                inFlight += name
                throw TipOperationPendingException(name)
            }
            inFlight -= name
            if (name in failing) throw IOException("$name failed")
        }

        override fun finishPendingDeletion() {
            allCalls += "finishPendingDeletion"
            val deletions = listOf("deleteToken", "deleteInstallation")
            deletions.firstOrNull { it in inFlight && it in pending }?.let {
                throw TipOperationPendingException(it)
            }
            inFlight -= deletions.toSet()
        }

        override fun subscribe() = call("subscribe")
        override fun unsubscribe() = call("unsubscribe")
        override fun deleteToken() = call("deleteToken")
        override fun deleteInstallation() = call("deleteInstallation")
    }

    private val state = FakeState()
    private val client = FakeClient()
    private var now = 1_000_000L
    private var versionCode = 10

    private fun reconciler() = TipRegistrationReconciler(state, versionCode) { now }

    @Test
    fun doesNothingBeforeAnyOptIn() {
        assertFalse(reconciler().needsWork())

        reconciler().reconcile(client)

        assertTrue(client.calls.isEmpty())
        assertFalse(state.registrationMayExist)
    }

    @Test
    fun subscribesOnceAfterOptIn() {
        state.enabled = true
        assertTrue(reconciler().needsWork())

        reconciler().reconcile(client)
        reconciler().reconcile(client)

        assertEquals(listOf("subscribe"), client.calls)
        assertTrue(state.registrationMayExist)
        assertFalse(reconciler().needsWork())
    }

    @Test
    fun subscribesAgainAfterAWeekOrAnAppUpdate() {
        state.enabled = true
        reconciler().reconcile(client)

        now += TipRegistrationReconciler.REFRESH_INTERVAL_MILLIS - 1
        assertFalse(reconciler().needsWork())
        now += 1
        assertTrue(reconciler().needsWork())
        reconciler().reconcile(client)
        assertFalse(reconciler().needsWork())

        versionCode = 11
        assertTrue(reconciler().needsWork())
        reconciler().reconcile(client)

        assertEquals(listOf("subscribe", "subscribe", "subscribe"), client.calls)
    }

    @Test
    fun aFailedSubscribeIsRetriedAndRememberedAsAPossibleRegistration() {
        state.enabled = true
        client.failing += "subscribe"

        assertThrows(IOException::class.java) { reconciler().reconcile(client) }

        assertTrue(state.registrationMayExist)
        assertTrue(reconciler().needsWork())

        client.failing.clear()
        reconciler().reconcile(client)
        assertFalse(reconciler().needsWork())
    }

    @Test
    fun optingOutRemovesTheSubscriptionTokenAndInstallationInOrder() {
        state.enabled = true
        reconciler().reconcile(client)
        client.allCalls.clear()

        state.enabled = false
        assertTrue(reconciler().needsWork())
        reconciler().reconcile(client)

        assertEquals(listOf("unsubscribe", "deleteToken", "deleteInstallation"), client.calls)
        assertFalse(state.registrationMayExist)
        assertFalse(reconciler().needsWork())
    }

    @Test
    fun aFailedUnsubscribeDoesNotBlockDeletion() {
        state.registrationMayExist = true
        client.failing += "unsubscribe"

        reconciler().reconcile(client)

        assertEquals(listOf("unsubscribe", "deleteToken", "deleteInstallation"), client.calls)
        assertFalse(state.registrationMayExist)
    }

    @Test
    fun aFailedDeletionKeepsRemovalPendingUntilItSucceeds() {
        state.registrationMayExist = true

        listOf("deleteToken", "deleteInstallation").forEach { step ->
            client.failing.clear()
            client.failing += step
            assertThrows(IOException::class.java) { reconciler().reconcile(client) }
            assertTrue(state.registrationMayExist)
            assertTrue(reconciler().needsWork())
        }

        client.failing.clear()
        reconciler().reconcile(client)
        assertFalse(state.registrationMayExist)
    }

    @Test
    fun optingBackInAfterAPendingRemovalSubscribesAgain() {
        state.enabled = true
        reconciler().reconcile(client)
        state.enabled = false
        client.failing += "deleteToken"
        assertThrows(IOException::class.java) { reconciler().reconcile(client) }
        client.failing.clear()
        client.allCalls.clear()

        state.enabled = true
        assertTrue(reconciler().needsWork())
        reconciler().reconcile(client)

        assertEquals(listOf("subscribe"), client.calls)
        assertTrue(state.registrationMayExist)
        assertFalse(reconciler().needsWork())
    }

    @Test
    fun anUnsubscribeStillInFlightHoldsBackDeletionUntilItSettles() {
        state.registrationMayExist = true
        client.pending += "unsubscribe"

        assertThrows(TipOperationPendingException::class.java) { reconciler().reconcile(client) }

        assertEquals(listOf("unsubscribe"), client.calls)
        assertTrue(state.registrationMayExist)

        client.pending.clear()
        client.allCalls.clear()
        reconciler().reconcile(client)

        assertEquals(listOf("unsubscribe", "deleteToken", "deleteInstallation"), client.calls)
        assertFalse(state.registrationMayExist)
    }

    @Test
    fun aDeletionStillInFlightHoldsBackANewSubscription() {
        state.registrationMayExist = true
        client.pending += "deleteToken"
        assertThrows(TipOperationPendingException::class.java) { reconciler().reconcile(client) }
        client.allCalls.clear()

        state.enabled = true
        assertThrows(TipOperationPendingException::class.java) { reconciler().reconcile(client) }

        assertTrue(client.calls.isEmpty())
        assertTrue(reconciler().needsWork())

        client.pending.clear()
        reconciler().reconcile(client)

        assertEquals(listOf("subscribe"), client.calls)
        assertFalse(reconciler().needsWork())
    }

    @Test
    fun reportsHowLongUntilTheNextRefreshOnlyWhileSubscribed() {
        assertEquals(null, reconciler().millisUntilRefresh())

        state.enabled = true
        assertEquals(null, reconciler().millisUntilRefresh())
        reconciler().reconcile(client)
        assertEquals(TipRegistrationReconciler.REFRESH_INTERVAL_MILLIS, reconciler().millisUntilRefresh())

        now += 1_000
        assertEquals(TipRegistrationReconciler.REFRESH_INTERVAL_MILLIS - 1_000, reconciler().millisUntilRefresh())

        state.enabled = false
        assertEquals(null, reconciler().millisUntilRefresh())
    }

    @Test
    fun aDeletionThatFinishesLateResumesWithoutUnsubscribingAgain() {
        listOf("deleteToken", "deleteInstallation").forEach { step ->
            state.enabled = true
            reconciler().reconcile(client)
            state.enabled = false
            client.allCalls.clear()
            client.pending += step
            assertThrows(TipOperationPendingException::class.java) { reconciler().reconcile(client) }
            assertTrue(state.registrationMayExist)

            client.pending.clear()
            client.allCalls.clear()
            reconciler().reconcile(client)

            assertFalse("unsubscribe ran again after $step", "unsubscribe" in client.calls)
            assertEquals(step, client.calls.first())
            assertEquals("deleteInstallation", client.calls.last())
            assertFalse(state.registrationMayExist)
            assertEquals(TipRegistrationReconciler.REMOVAL_NOT_STARTED, state.removalStep)
        }
    }

    @Test
    fun optingBackInStartsTheNextRemovalFromTheBeginning() {
        state.registrationMayExist = true
        client.failing += "deleteInstallation"
        assertThrows(IOException::class.java) { reconciler().reconcile(client) }
        assertEquals(TipRegistrationReconciler.REMOVAL_TOKEN_DELETED, state.removalStep)
        client.failing.clear()

        state.enabled = true
        reconciler().reconcile(client)
        assertEquals(TipRegistrationReconciler.REMOVAL_NOT_STARTED, state.removalStep)

        state.enabled = false
        client.allCalls.clear()
        reconciler().reconcile(client)

        assertEquals(listOf("unsubscribe", "deleteToken", "deleteInstallation"), client.calls)
    }

    @Test
    fun aRemovalThatStartsOverFirstSettlesEarlierDeletions() {
        state.registrationMayExist = true

        reconciler().reconcile(client)

        assertEquals(
            listOf("finishPendingDeletion", "unsubscribe", "deleteToken", "deleteInstallation"),
            client.allCalls
        )
    }

    @Test
    fun aRemovalDoesNotStartOverWhileAnEarlierDeletionIsStillInFlight() {
        state.registrationMayExist = true
        client.pending += "deleteInstallation"
        assertThrows(TipOperationPendingException::class.java) { reconciler().reconcile(client) }
        state.removalStep = TipRegistrationReconciler.REMOVAL_NOT_STARTED
        client.allCalls.clear()

        assertThrows(TipOperationPendingException::class.java) { reconciler().reconcile(client) }

        assertTrue(client.calls.isEmpty())
        assertTrue(state.registrationMayExist)
    }

    @Test
    fun aResumedRemovalDoesNotDiscardTheDeletionItIsWaitingFor() {
        state.registrationMayExist = true
        state.removalStep = TipRegistrationReconciler.REMOVAL_TOKEN_DELETED

        reconciler().reconcile(client)

        assertEquals(listOf("deleteInstallation"), client.allCalls)
    }
}
