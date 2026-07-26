package com.enaboapps.switchify.pc.bluetooth

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothFrameCodecTest {
    @Test
    fun singleFrameRoundTrip() {
        val frame = BluetoothFrameCodec.createFrames("""{"type":"connection.ping"}""", messageId = "message-1").single()
        val decoded = BluetoothFrameCodec.decode(BluetoothFrameCodec.encode(frame))

        assertEquals(frame, decoded)
        assertEquals(
            BluetoothFrameReassemblyResult.Complete("""{"type":"connection.ping"}"""),
            BluetoothFrameReassembler().accept(frame)
        )
    }

    @Test
    fun multiFrameRoundTrip() {
        val frames = BluetoothFrameCodec.createFrames("abcdefghijklmnopqrstuvwxyz", messageId = "message-1", maxPayloadBytes = 5)
        val reassembler = BluetoothFrameReassembler()

        frames.dropLast(1).forEach { frame ->
            assertEquals(BluetoothFrameReassemblyResult.Incomplete, reassembler.accept(frame))
        }
        assertEquals(
            BluetoothFrameReassemblyResult.Complete("abcdefghijklmnopqrstuvwxyz"),
            reassembler.accept(frames.last())
        )
    }

    @Test
    fun missingMiddleChunkStaysIncomplete() {
        val frames = BluetoothFrameCodec.createFrames("abcdefghijklmnopqrstuvwxyz", messageId = "message-1", maxPayloadBytes = 5)
        val reassembler = BluetoothFrameReassembler()

        assertEquals(BluetoothFrameReassemblyResult.Incomplete, reassembler.accept(frames.first()))
        assertEquals(BluetoothFrameReassemblyResult.Incomplete, reassembler.accept(frames.last()))
    }

    @Test
    fun duplicateSequenceIsIgnored() {
        val frames = BluetoothFrameCodec.createFrames("abcdef", messageId = "message-1", maxPayloadBytes = 3)
        val reassembler = BluetoothFrameReassembler()

        assertEquals(BluetoothFrameReassemblyResult.Incomplete, reassembler.accept(frames.first()))
        assertEquals(BluetoothFrameReassemblyResult.Incomplete, reassembler.accept(frames.first()))
        assertEquals(BluetoothFrameReassemblyResult.Complete("abcdef"), reassembler.accept(frames.last()))
    }

    @Test
    fun rejectsInvalidFrames() {
        assertEquals(BluetoothFrameRejectReason.InvalidFrame, BluetoothFrameCodec.validate(validFrame().copy(version = 2)))
        assertEquals(BluetoothFrameRejectReason.InvalidFrame, BluetoothFrameCodec.validate(validFrame().copy(messageId = "")))
        assertEquals(BluetoothFrameRejectReason.InvalidFrame, BluetoothFrameCodec.validate(validFrame().copy(sequence = -1)))
        assertEquals(BluetoothFrameRejectReason.InvalidFrame, BluetoothFrameCodec.validate(validFrame().copy(payloadBase64 = "***")))
        assertEquals(BluetoothFrameRejectReason.MessageTooLarge, BluetoothFrameCodec.validate(validFrame().copy(totalBytes = 4), maxMessageBytes = 3))
    }

    @Test
    fun partialMessageExpires() {
        var now = 1_000L
        val first = BluetoothFrameCodec.createFrames("abcdefghijklmnopqrstuvwxyz", messageId = "message-1", maxPayloadBytes = 5).first()
        val reassembler = BluetoothFrameReassembler(now = { now }, partialTimeoutMs = 100)

        assertEquals(BluetoothFrameReassemblyResult.Incomplete, reassembler.accept(first))
        now = 1_101L

        assertEquals(1, reassembler.clearExpired())
    }

    @Test
    fun frameJsonUsesPcFieldNames() {
        val frame = BluetoothFrameCodec.createFrames("abc", messageId = "message-1").single()
        val json = JSONObject(String(BluetoothFrameCodec.encode(frame)))

        assertTrue(json.has("version"))
        assertTrue(json.has("messageId"))
        assertTrue(json.has("sequence"))
        assertTrue(json.has("isFinal"))
        assertTrue(json.has("totalBytes"))
        assertTrue(json.has("payloadBase64"))
    }

    @Test
    fun responseRouterCompletesOnlyTheMatchingRequest() = runTest {
        val router = PcBleResponseRouter()
        val first = router.register("request-1")
        val second = router.register("request-2")
        val secondMessage = """{"id":"request-2","type":"ack"}"""

        assertFalse(router.route("""{"id":"stale","type":"ack"}"""))
        assertTrue(router.route(secondMessage))
        assertEquals(secondMessage, second.await())
        assertFalse(first.isCompleted)
    }

    @Test
    fun responseRouterFailsEveryPendingRequestOnDisconnect() = runTest {
        val router = PcBleResponseRouter()
        val first = router.register("request-1")
        val second = router.register("request-2")

        router.fail(IllegalStateException("disconnected"))

        assertEquals("disconnected", runCatching { first.await() }.exceptionOrNull()?.message)
        assertEquals("disconnected", runCatching { second.await() }.exceptionOrNull()?.message)
    }

    @Test
    fun noResponseWriteFallsBackWhenCharacteristicDoesNotSupportIt() {
        assertEquals(
            PcBleWriteMode.WithoutResponse,
            resolveBleWriteMode(PcBleWriteMode.WithoutResponse, supportsWithoutResponse = true)
        )
        assertEquals(
            PcBleWriteMode.WithResponse,
            resolveBleWriteMode(PcBleWriteMode.WithoutResponse, supportsWithoutResponse = false)
        )
        assertEquals(
            PcBleWriteMode.WithResponse,
            resolveBleWriteMode(PcBleWriteMode.WithResponse, supportsWithoutResponse = true)
        )
    }

    private fun validFrame(): BluetoothFrame {
        return BluetoothFrameCodec.createFrames("abc", messageId = "message-1").single()
    }
}
