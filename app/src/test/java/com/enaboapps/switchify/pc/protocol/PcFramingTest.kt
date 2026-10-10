package com.enaboapps.switchify.pc.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PcFramingTest {
    @Test
    fun roundTripsSingleAndMultiFrameUtf8Messages() {
        val message = "Switchify 👋 remote"
        val frames = PcFraming.createFrames(message, "message-1", 5)
        val reassembler = PcFrameReassembler()
        frames.dropLast(1).forEach { assertEquals(PcReassemblyResult.Incomplete, reassembler.accept(it)) }
        assertEquals(PcReassemblyResult.Complete(message), reassembler.accept(frames.last()))
        assertEquals(frames[0], PcFraming.decodeFrame(PcFraming.encodeFrame(frames[0])))
    }

    @Test
    fun encodesFramesWithTheDesktopFieldOrder() {
        val frame = PcFraming.createFrames("abc", "message-1").single()
        assertEquals(
            "{\"version\":1,\"messageId\":\"message-1\",\"sequence\":0,\"isFinal\":true,\"totalBytes\":3,\"payloadBase64\":\"YWJj\"}",
            PcFraming.encodeFrame(frame).toString(Charsets.UTF_8)
        )
    }

    @Test
    fun decodesFramesEncodedByTheDesktop() {
        val desktopFrame = "{\"version\":1,\"messageId\":\"8a3c\",\"sequence\":0,\"isFinal\":true,\"totalBytes\":45," +
            "\"payloadBase64\":\"eyJ2ZXJzaW9uIjoxLCJpZCI6ImEiLCJ0eXBlIjoiYWNrIiwib2siOnRydWV9\"}"
        val frame = PcFraming.decodeFrame(desktopFrame.toByteArray())!!
        assertEquals(
            PcReassemblyResult.Complete("{\"version\":1,\"id\":\"a\",\"type\":\"ack\",\"ok\":true}"),
            PcFrameReassembler().accept(frame)
        )
    }

    @Test
    fun rejectsMalformedFrameValues() {
        listOf(
            "not json",
            "{\"version\":1}",
            "{\"version\":2,\"messageId\":\"m\",\"sequence\":0,\"isFinal\":true,\"totalBytes\":0,\"payloadBase64\":\"\"}",
            "{\"version\":1,\"messageId\":\"\",\"sequence\":0,\"isFinal\":true,\"totalBytes\":0,\"payloadBase64\":\"\"}",
            "{\"version\":1,\"messageId\":\"m\",\"sequence\":-1,\"isFinal\":true,\"totalBytes\":0,\"payloadBase64\":\"\"}",
            "{\"version\":1,\"messageId\":\"m\",\"sequence\":0.5,\"isFinal\":true,\"totalBytes\":0,\"payloadBase64\":\"\"}",
            "{\"version\":1,\"messageId\":\"m\",\"sequence\":0,\"isFinal\":true,\"totalBytes\":1,\"payloadBase64\":\"!!\"}"
        ).forEach { assertNull(it, PcFraming.decodeFrame(it.toByteArray())) }
    }

    @Test
    fun rejectsInvalidAndOversizedFramesAndExpiresPartialMessages() {
        val frame = PcFraming.createFrames("abc", "message-1").single()
        assertEquals(PcFrameRejectReason.InvalidFrame, PcFraming.validateFrame(frame.copy(version = 2)))
        assertEquals(PcFrameRejectReason.MessageTooLarge, PcFraming.validateFrame(frame.copy(totalBytes = 4), 3))
        assertEquals(
            PcReassemblyResult.Rejected(PcFrameRejectReason.MessageTooLarge),
            PcFrameReassembler().accept(frame.copy(totalBytes = PcProtocolConstants.MAX_MESSAGE_BYTES + 1))
        )
        var now = 1000L
        val reassembler = PcFrameReassembler(now = { now }, timeoutMs = 100)
        assertEquals(PcReassemblyResult.Incomplete, reassembler.accept(frame.copy(isFinal = false)))
        now = 1101L
        assertEquals(1, reassembler.clearExpired())
    }

    @Test
    fun usesTheTenSecondPartialTimeoutByDefault() {
        var now = 0L
        val reassembler = PcFrameReassembler(now = { now })
        val frame = PcFraming.createFrames("ab", "partial", 1).first()
        assertEquals(PcReassemblyResult.Incomplete, reassembler.accept(frame))
        now = 9_999L
        assertEquals(0, reassembler.clearExpired())
        now = 10_000L
        assertEquals(1, reassembler.clearExpired())
    }

    @Test
    fun adaptsEveryEncodedFrameToTheNegotiatedWriteLimit() {
        val message = "🙂".repeat(300)
        val frames = PcFraming.createFramesForWriteLimit(message, "message-with-a-long-identifier", 182)
        assertTrue(frames.size > 1)
        assertTrue(frames.maxOf { PcFraming.encodeFrame(it).size } <= 182)
        val reassembler = PcFrameReassembler()
        var result: PcReassemblyResult = PcReassemblyResult.Incomplete
        frames.forEach { result = reassembler.accept(it) }
        assertEquals(PcReassemblyResult.Complete(message), result)
        assertThrows(PcFramingException::class.java) { PcFraming.createFramesForWriteLimit("a", "message", 20) }
    }

    @Test
    fun neverExceedsTheMaximumInnerPayloadForLargeWriteLimits() {
        val frames = PcFraming.createFramesForWriteLimit("x".repeat(1000), "m", 514)
        assertTrue(frames.all { Base64.getDecoder().decode(it.payloadBase64).size <= 160 })
        assertEquals(160, Base64.getDecoder().decode(frames.first().payloadBase64).size)
    }

    @Test
    fun enforcesTheSixteenKibMessageLimit() {
        PcFraming.createFrames("x".repeat(16 * 1024), "max")
        assertThrows(PcFramingException::class.java) { PcFraming.createFrames("x".repeat(16 * 1024 + 1), "over") }
    }

    @Test
    fun ignoresDuplicateFragmentsAndCompletesAfterOutOfOrderDelivery() {
        val frames = PcFraming.createFrames("fragmented message", "duplicate-test", 4)
        val reassembler = PcFrameReassembler()
        assertEquals(PcReassemblyResult.Incomplete, reassembler.accept(frames[0]))
        assertEquals(PcReassemblyResult.Incomplete, reassembler.accept(frames[0]))
        assertEquals(PcReassemblyResult.Incomplete, reassembler.accept(frames.last()))
        var result = reassembler.accept(frames[1])
        frames.subList(2, frames.size - 1).forEach { result = reassembler.accept(it) }
        assertEquals(PcReassemblyResult.Complete("fragmented message"), result)
    }

    @Test
    fun matchesTheDesktopSwapAndDuplicateRoundTrip() {
        val message = "x".repeat(500)
        val frames = PcFraming.createFrames(message, "desktop").toMutableList()
        val duplicate = frames[1]
        frames[0] = frames[2].also { frames[2] = frames[0] }
        frames.add(2, duplicate)
        val reassembler = PcFrameReassembler()
        val results = frames.map { reassembler.accept(it) }
        assertEquals(1, results.count { it == PcReassemblyResult.Complete(message) })
    }

    @Test
    fun boundsConcurrentPartialMessagesToEight() {
        val reassembler = PcFrameReassembler()
        fun partial(id: String, sequence: Int, isFinal: Boolean, payload: String) =
            PcBluetoothFrame(1, id, sequence, isFinal, 2, Base64.getEncoder().encodeToString(payload.toByteArray()))
        repeat(PcProtocolConstants.MAX_PARTIAL_MESSAGES) {
            assertEquals(PcReassemblyResult.Incomplete, reassembler.accept(partial("partial-$it", 0, false, "a")))
        }
        assertEquals(
            PcReassemblyResult.Rejected(PcFrameRejectReason.InvalidFrame),
            reassembler.accept(partial("partial-overflow", 0, false, "a"))
        )
        assertEquals(PcReassemblyResult.Complete("ab"), reassembler.accept(partial("partial-0", 1, true, "b")))
    }

    @Test
    fun rejectsInconsistentTotalsAndOverlongPayloads() {
        val reassembler = PcFrameReassembler()
        val first = PcBluetoothFrame(1, "aggregate", 0, false, 2, Base64.getEncoder().encodeToString("aa".toByteArray()))
        assertEquals(PcReassemblyResult.Incomplete, reassembler.accept(first))
        assertEquals(
            PcReassemblyResult.Rejected(PcFrameRejectReason.InvalidFrame),
            reassembler.accept(first.copy(sequence = 1, isFinal = true, payloadBase64 = "Yg=="))
        )
        val mismatch = PcFrameReassembler()
        mismatch.accept(first.copy(messageId = "mismatch"))
        assertEquals(
            PcReassemblyResult.Rejected(PcFrameRejectReason.InvalidFrame),
            mismatch.accept(first.copy(messageId = "mismatch", sequence = 1, totalBytes = 3))
        )
    }

    @Test
    fun emptyMessagesUseOneFinalEmptyFrame() {
        val frame = PcFraming.createFrames("", "empty").single()
        assertTrue(frame.isFinal)
        assertEquals("", frame.payloadBase64)
        assertEquals(PcReassemblyResult.Complete(""), PcFrameReassembler().accept(frame))
    }
}
