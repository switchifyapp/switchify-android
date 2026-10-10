package com.enaboapps.switchify.pc.protocol

import org.json.JSONObject
import java.util.Base64

data class PcBluetoothFrame(
    val version: Int,
    val messageId: String,
    val sequence: Int,
    val isFinal: Boolean,
    val totalBytes: Int,
    val payloadBase64: String
)

enum class PcFrameRejectReason {
    InvalidFrame,
    MessageTooLarge,
    Expired
}

sealed class PcReassemblyResult {
    data class Complete(val message: String) : PcReassemblyResult()
    data object Incomplete : PcReassemblyResult()
    data class Rejected(val reason: PcFrameRejectReason) : PcReassemblyResult()
}

class PcFramingException(message: String) : IllegalArgumentException(message)

object PcFraming {
    fun createFrames(
        message: String,
        messageId: String,
        maxPayloadBytes: Int = PcProtocolConstants.DEFAULT_FRAME_PAYLOAD_BYTES,
        maxMessageBytes: Int = PcProtocolConstants.MAX_MESSAGE_BYTES
    ): List<PcBluetoothFrame> {
        if (maxPayloadBytes <= 0) throw PcFramingException("Bluetooth frame payload size must be positive.")
        val bytes = message.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxMessageBytes) throw PcFramingException("Bluetooth message is too large.")
        val encoder = Base64.getEncoder()
        val frames = mutableListOf<PcBluetoothFrame>()
        var offset = 0
        var sequence = 0
        do {
            val end = minOf(offset + maxPayloadBytes, bytes.size)
            frames += PcBluetoothFrame(
                version = PcProtocolConstants.FRAME_VERSION,
                messageId = messageId,
                sequence = sequence,
                isFinal = end >= bytes.size,
                totalBytes = bytes.size,
                payloadBase64 = encoder.encodeToString(bytes.copyOfRange(offset, end))
            )
            offset += maxPayloadBytes
            sequence += 1
        } while (offset < bytes.size)
        return frames
    }

    fun encodeFrame(frame: PcBluetoothFrame): ByteArray = PcJson.stringify(
        linkedMapOf(
            "version" to frame.version,
            "messageId" to frame.messageId,
            "sequence" to frame.sequence,
            "isFinal" to frame.isFinal,
            "totalBytes" to frame.totalBytes,
            "payloadBase64" to frame.payloadBase64
        )
    ).toByteArray(Charsets.UTF_8)

    fun createFramesForWriteLimit(message: String, messageId: String, maxWriteValueBytes: Int): List<PcBluetoothFrame> {
        val messageBytes = message.toByteArray(Charsets.UTF_8).size
        var low = 1
        var high = maxOf(1, minOf(PcProtocolConstants.DEFAULT_FRAME_PAYLOAD_BYTES, messageBytes))
        var fitting: List<PcBluetoothFrame>? = null
        while (low <= high) {
            val payloadBytes = (low + high) / 2
            val frames = createFrames(message, messageId, payloadBytes)
            if (frames.all { encodeFrame(it).size <= maxWriteValueBytes }) {
                fitting = frames
                low = payloadBytes + 1
            } else {
                high = payloadBytes - 1
            }
        }
        return fitting ?: throw PcFramingException("Negotiated Bluetooth write size is too small for protocol frames.")
    }

    fun decodeFrame(value: ByteArray): PcBluetoothFrame? = try {
        val json = JSONObject(String(value, Charsets.UTF_8))
        val frame = PcBluetoothFrame(
            version = json.requireInteger("version"),
            messageId = json.get("messageId") as String,
            sequence = json.requireInteger("sequence"),
            isFinal = json.get("isFinal") as Boolean,
            totalBytes = json.requireInteger("totalBytes"),
            payloadBase64 = json.get("payloadBase64") as String
        )
        if (validateFrame(frame) == null) frame else null
    } catch (_: Exception) {
        null
    }

    fun validateFrame(
        frame: PcBluetoothFrame,
        maxMessageBytes: Int = PcProtocolConstants.MAX_MESSAGE_BYTES
    ): PcFrameRejectReason? {
        if (frame.totalBytes > maxMessageBytes) return PcFrameRejectReason.MessageTooLarge
        if (frame.version != PcProtocolConstants.FRAME_VERSION ||
            frame.messageId.isEmpty() ||
            frame.sequence < 0 ||
            frame.totalBytes < 0
        ) {
            return PcFrameRejectReason.InvalidFrame
        }
        return if (decodePayload(frame.payloadBase64) == null) PcFrameRejectReason.InvalidFrame else null
    }

    internal fun decodePayload(payloadBase64: String): ByteArray? = try {
        Base64.getDecoder().decode(payloadBase64)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun JSONObject.requireInteger(key: String): Int {
        val number = get(key) as Number
        val value = number.toDouble()
        require(value % 1.0 == 0.0 && value >= Int.MIN_VALUE && value <= Int.MAX_VALUE)
        return value.toInt()
    }
}

class PcFrameReassembler(
    private val now: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = PcProtocolConstants.PARTIAL_MESSAGE_TIMEOUT_MS,
    private val maxMessageBytes: Int = PcProtocolConstants.MAX_MESSAGE_BYTES,
    private val maxPartialMessages: Int = PcProtocolConstants.MAX_PARTIAL_MESSAGES
) {
    private class Partial(val totalBytes: Int, val createdAt: Long) {
        val chunks = HashMap<Int, ByteArray>()
        var finalSequence: Int? = null
    }

    private val partials = LinkedHashMap<String, Partial>()

    @Synchronized
    fun accept(frame: PcBluetoothFrame): PcReassemblyResult {
        PcFraming.validateFrame(frame, maxMessageBytes)?.let { return PcReassemblyResult.Rejected(it) }
        clearExpired()
        val existing = partials[frame.messageId]
        if (existing == null && partials.size >= maxPartialMessages) {
            return PcReassemblyResult.Rejected(PcFrameRejectReason.InvalidFrame)
        }
        val partial = existing ?: Partial(frame.totalBytes, now())
        if (partial.totalBytes != frame.totalBytes) {
            partials.remove(frame.messageId)
            return PcReassemblyResult.Rejected(PcFrameRejectReason.InvalidFrame)
        }
        if (!partial.chunks.containsKey(frame.sequence)) {
            val chunk = PcFraming.decodePayload(frame.payloadBase64)
                ?: return PcReassemblyResult.Rejected(PcFrameRejectReason.InvalidFrame)
            partial.chunks[frame.sequence] = chunk
        }
        if (frame.isFinal) partial.finalSequence = frame.sequence
        partials[frame.messageId] = partial
        val finalSequence = partial.finalSequence ?: return PcReassemblyResult.Incomplete
        val chunks = mutableListOf<ByteArray>()
        var size = 0
        var sequence = 0
        while (sequence <= finalSequence) {
            val chunk = partial.chunks[sequence] ?: break
            chunks += chunk
            size += chunk.size
            if (size > partial.totalBytes) {
                partials.remove(frame.messageId)
                return PcReassemblyResult.Rejected(PcFrameRejectReason.InvalidFrame)
            }
            sequence += 1
        }
        if (size != partial.totalBytes) return PcReassemblyResult.Incomplete
        partials.remove(frame.messageId)
        val combined = ByteArray(size)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(combined, offset)
            offset += chunk.size
        }
        return PcReassemblyResult.Complete(String(combined, Charsets.UTF_8))
    }

    @Synchronized
    fun clearExpired(): Int {
        val deadline = now() - timeoutMs
        val expired = partials.filterValues { it.createdAt <= deadline }.keys
        expired.forEach(partials::remove)
        return expired.size
    }

    @Synchronized
    fun clear() {
        partials.clear()
    }
}
