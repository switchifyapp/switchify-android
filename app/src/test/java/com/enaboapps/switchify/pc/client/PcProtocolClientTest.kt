package com.enaboapps.switchify.pc.client

import com.enaboapps.switchify.pc.protocol.PcCanonical
import com.enaboapps.switchify.pc.protocol.PcClock
import com.enaboapps.switchify.pc.protocol.PcCommands
import com.enaboapps.switchify.pc.protocol.PcFrameReassembler
import com.enaboapps.switchify.pc.protocol.PcFraming
import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcReassemblyResult
import com.enaboapps.switchify.pc.protocol.PcResponse
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import com.enaboapps.switchify.pc.transport.PcBluetoothAvailability
import com.enaboapps.switchify.pc.transport.PcBluetoothException
import com.enaboapps.switchify.pc.transport.PcDiscoveredDesktop
import com.enaboapps.switchify.pc.transport.PcTransport
import com.enaboapps.switchify.pc.transport.PcUnsubscribe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcProtocolClientTest {
    private class FakeTransport : PcTransport {
        val frames = mutableListOf<ByteArray>()
        var listener: ((ByteArray) -> Unit)? = null
        var errorListener: ((Throwable) -> Unit)? = null
        var fail = false
        var writeGate: CompletableDeferred<Unit>? = null
        var notificationsError: Exception? = null
        var inFlight = 0
        var maxInFlight = 0
        var cancelledWrites = 0

        override suspend fun availability() = PcBluetoothAvailability.Ready
        override fun scan(onDesktop: (PcDiscoveredDesktop) -> Unit, onError: (Throwable) -> Unit) = PcUnsubscribe {}
        override suspend fun connect(peripheralId: String) = Unit
        override suspend fun resolveAndConnect(desktopId: String): PcDiscoveredDesktop = throw UnsupportedOperationException()
        override suspend fun disconnect() = Unit
        override fun maxWriteValueBytes() = 182
        override suspend fun cancelPendingWrites() {
            cancelledWrites += 1
        }
        override suspend fun verifyConnection(desktopId: String) = true
        override suspend fun notificationsReady() {
            notificationsError?.let { throw it }
        }
        override fun subscribeDisconnect(onDisconnect: () -> Unit) = PcUnsubscribe {}

        override suspend fun writeFrame(frame: ByteArray) {
            if (fail) throw PcBluetoothException("Bluetooth write failed.")
            inFlight += 1
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                writeGate?.await()
                frames += frame
            } finally {
                inFlight -= 1
            }
        }

        override fun subscribe(onFrame: (ByteArray) -> Unit, onError: (Throwable) -> Unit): PcUnsubscribe {
            listener = onFrame
            errorListener = onError
            return PcUnsubscribe {
                listener = null
                errorListener = null
            }
        }

        fun emit(response: String) {
            PcFraming.createFrames(response, "response").forEach { listener?.invoke(PcFraming.encodeFrame(it)) }
        }

        fun sentMessages(): List<String> {
            val reassembler = PcFrameReassembler()
            return frames.mapNotNull { raw ->
                (reassembler.accept(PcFraming.decodeFrame(raw)!!) as? PcReassemblyResult.Complete)?.message
            }
        }
    }

    private val transport = FakeTransport()

    @Test
    fun serializesMtuSafeWritesThatReassembleToTheOriginalMessage() = runTest {
        val client = PcProtocolClient(transport, backgroundScope, PcIdGenerator { "message" })
        val message = "{\"text\":\"${"safe fixture ".repeat(100)}\"}"
        client.send(message)
        assertTrue(transport.frames.size > 1)
        assertTrue(transport.frames.all { it.size <= 182 })
        assertEquals(listOf(message), transport.sentMessages())
    }

    @Test
    fun correlatesOnlyTheRequestedResponseAndIgnoresStaleNotifications() = runTest {
        val client = PcProtocolClient(transport, backgroundScope)
        client.start {}
        val request = async { client.request("{\"fixture\":true}", "wanted") }
        runCurrent()
        transport.emit("{\"type\":\"ack\",\"id\":\"stale\",\"ok\":true,\"error\":null}")
        assertFalse(request.isCompleted)
        transport.emit("{\"type\":\"ack\",\"id\":\"wanted\",\"ok\":true,\"error\":null}")
        assertEquals(PcResponse.Ack("wanted"), request.await())
    }

    @Test
    fun serializesConcurrentGattWrites() = runTest {
        var id = 0
        val client = PcProtocolClient(transport, backgroundScope, PcIdGenerator { "message-${++id}" })
        transport.writeGate = CompletableDeferred()
        val first = async { client.send("first") }
        val second = async { client.send("second") }
        runCurrent()
        assertEquals(1, transport.maxInFlight)
        transport.writeGate!!.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, transport.maxInFlight)
        assertEquals(listOf("first", "second"), transport.sentMessages())
    }

    @Test
    fun rejectsWriteFailuresAndMissingResponses() = runTest {
        val client = PcProtocolClient(transport, backgroundScope)
        transport.fail = true
        assertTrue(runCatching { client.request("{\"fixture\":true}", "write", 10) }.exceptionOrNull() is PcProtocolWriteException)
        transport.fail = false
        val timeout = async { runCatching { client.request("{\"fixture\":true}", "timeout", 1) } }
        advanceTimeBy(2)
        assertTrue(timeout.await().exceptionOrNull() is PcProtocolResponseTimeoutException)
    }

    @Test
    fun boundsANativeWriteThatNeverSettles() = runTest {
        val client = PcProtocolClient(transport, backgroundScope, PcIdGenerator { "hung-write" }, writeTimeoutMs = 1)
        transport.writeGate = CompletableDeferred()
        val send = async { runCatching { client.send("never") } }
        advanceTimeBy(2)
        assertEquals("Bluetooth write timed out.", send.await().exceptionOrNull()?.message)
        assertEquals(1, transport.cancelledWrites)
    }

    @Test
    fun failsPendingRequestsWhenTheTransportFails() = runTest {
        val client = PcProtocolClient(transport, backgroundScope)
        var failures = 0
        client.start { failures += 1 }
        val request = async { runCatching { client.request("{}", "pending") } }
        runCurrent()
        transport.errorListener!!.invoke(PcBluetoothException("Bluetooth response reading failed. Reconnect to the PC."))
        runCurrent()
        assertEquals(1, failures)
        assertEquals("PC disconnected.", request.await().exceptionOrNull()?.message)
        assertEquals(null, transport.listener)
    }

    @Test
    fun releasesTheSubscriptionWhenNotificationsAreNotReady() = runTest {
        val client = PcProtocolClient(transport, backgroundScope)
        transport.notificationsError = PcBluetoothException("Bluetooth notifications could not be enabled.")
        assertTrue(runCatching { client.start {} }.isFailure)
        assertEquals(null, transport.listener)
    }

    @Test
    fun authenticatedChannelSignsCommandsWithTheSessionCredentials() = runTest {
        val client = PcProtocolClient(transport, backgroundScope)
        client.start {}
        val credentials = PcCredentials("device-1", "shared-token")
        assertFalse(credentials.toString().contains("shared-token"))
        val channel = PcAuthenticatedCommandChannel(client, credentials, PcClock { 1000L }, PcIdGenerator { "req-1" })
        val response = async { channel.send(PcCommands.ping()) }
        runCurrent()
        val sent = JSONObject(transport.sentMessages().single())
        assertEquals("98bZHKWHa3ooOYZyXBuYpzOdbPWGW5FV04fEjxAl9sI", sent.getString("auth"))
        assertFalse(sent.toString().contains("shared-token"))
        transport.emit("{\"type\":\"ack\",\"id\":\"req-1\",\"ok\":true,\"error\":null}")
        assertTrue(response.await())
    }

    @Test
    fun noAckCommandsAreSentWithoutWaitingForAResponse() = runTest {
        val client = PcProtocolClient(transport, backgroundScope)
        client.start {}
        val channel = PcAuthenticatedCommandChannel(client, PcCredentials("device-1", "shared-token"), PcClock { 1000L }, PcIdGenerator { "move-1" })
        assertEquals(PcResponse.Ack("move-1"), channel.request(PcCommands.move(10.0, -5.0), PcResponseMode.None))
        val sent = JSONObject(transport.sentMessages().single())
        assertEquals("none", sent.getString("responseMode"))
        assertEquals(
            PcCanonical.authProof("move-1", "device-1", 1000L, "mouse.move", PcCommands.move(10.0, -5.0).payload, "shared-token", PcResponseMode.None),
            sent.getString("auth")
        )
    }

    @Test
    fun authenticatedChannelSurfacesDesktopErrors() = runTest {
        val client = PcProtocolClient(transport, backgroundScope)
        client.start {}
        val channel = PcAuthenticatedCommandChannel(client, PcCredentials("device-1", "t"), PcClock { 1L }, PcIdGenerator { "req-2" })
        val response = async { channel.request(PcCommands.pointerProfile()) }
        runCurrent()
        transport.emit("{\"version\":1,\"id\":\"req-2\",\"type\":\"error\",\"ok\":false,\"error\":{\"code\":\"invalid_auth\",\"message\":\"Command authentication failed.\"}}")
        assertEquals(PcResponse.Error("req-2", "invalid_auth", "Command authentication failed."), response.await())
    }
}
