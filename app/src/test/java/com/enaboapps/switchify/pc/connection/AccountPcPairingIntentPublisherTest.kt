package com.enaboapps.switchify.pc.connection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountPcPairingIntentPublisherTest {
    private val calls = mutableListOf<List<String>>()

    private fun publisher(
        signedIn: () -> Boolean = { true },
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
    fun cancellationIsRethrown() = runTest {
        try {
            publisher(create = { throw CancellationException("fixture cancelled") }).publish("desktop-1", "device-1", "nonce-1")
            fail("Expected cancellation")
        } catch (_: CancellationException) {
        }
    }
}
