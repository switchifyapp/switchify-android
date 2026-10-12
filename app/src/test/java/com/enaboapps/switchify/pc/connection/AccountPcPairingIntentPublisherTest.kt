package com.enaboapps.switchify.pc.connection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountPcPairingIntentPublisherTest {
    private val calls = mutableListOf<List<String>>()

    private fun publisher(
        signedIn: suspend () -> Boolean = { true },
        create: suspend () -> Unit = {}
    ) = AccountPcPairingIntentPublisher(
        isSignedIn = signedIn,
        createIntent = { desktopId, deviceId, nonce ->
            calls += listOf(desktopId, deviceId, nonce)
            create()
        }
    )

    @Test
    fun signedOutSkipsTheCall() = runTest {
        assertFalse(publisher(signedIn = { false }).publish("desktop-1", "device-1", "nonce-1"))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun signedInPublishesTheExactIdentifiers() = runTest {
        assertTrue(publisher().publish("desktop-1", "device-1", "nonce-1"))
        assertEquals(listOf(listOf("desktop-1", "device-1", "nonce-1")), calls)
    }

    @Test
    fun aFailedCallReturnsFalse() = runTest {
        assertFalse(publisher(create = { throw IllegalStateException("fixture rpc missing") }).publish("desktop-1", "device-1", "nonce-1"))
    }

    @Test
    fun aFailedSignedInCheckReturnsFalseWithoutACall() = runTest {
        assertFalse(publisher(signedIn = { throw IllegalStateException("fixture auth failed") }).publish("desktop-1", "device-1", "nonce-1"))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun aStrayCancellationWhileActiveReturnsFalse() = runTest {
        assertFalse(publisher(create = { throw CancellationException("fixture cancelled") }).publish("desktop-1", "device-1", "nonce-1"))
    }

    @Test
    fun genuineCancellationPropagates() = runTest {
        var returned: Boolean? = null
        val job = launch {
            returned = publisher(create = { awaitCancellation() }).publish("desktop-1", "device-1", "nonce-1")
        }
        runCurrent()
        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        assertNull(returned)
        assertEquals(1, calls.size)
    }

    @Test
    fun theSignedInCheckCanWaitForSessionLoading() = runTest {
        val sessionLoaded = CompletableDeferred<Boolean>()
        val result = async { publisher(signedIn = { sessionLoaded.await() }).publish("desktop-1", "device-1", "nonce-1") }
        runCurrent()
        assertTrue(calls.isEmpty())
        sessionLoaded.complete(true)
        assertTrue(result.await())
        assertEquals(1, calls.size)
    }
}
