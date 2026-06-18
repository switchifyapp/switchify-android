package com.enaboapps.switchify.pc.bluetooth

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

data class BluetoothFrame(
    val version: Int,
    val messageId: String,
    val sequence: Int,
    val isFinal: Boolean,
    val totalBytes: Int,
    val payloadBase64: String
)

sealed class BluetoothFrameReassemblyResult {
    data class Complete(val message: String) : BluetoothFrameReassemblyResult()
    data object Incomplete : BluetoothFrameReassemblyResult()
    data class Rejected(val reason: BluetoothFrameRejectReason) : BluetoothFrameReassemblyResult()
}

enum class BluetoothFrameRejectReason {
    InvalidFrame,
    MessageTooLarge,
    Expired
}

object BluetoothFrameCodec {
    fun createFrames(
        message: String,
        messageId: String = UUID.randomUUID().toString(),
        maxPayloadBytes: Int = PcBleConstants.defaultBluetoothFramePayloadBytes,
        maxMessageBytes: Int = PcBleConstants.defaultBluetoothMaxMessageBytes
    ): List<BluetoothFrame> {
        require(maxPayloadBytes > 0) { "Bluetooth frame payload size must be positive." }
        val payload = message.toByteArray(StandardCharsets.UTF_8)
        require(payload.size <= maxMessageBytes) { "Bluetooth message is too large." }
        val frames = mutableListOf<BluetoothFrame>()
        var offset = 0
        var sequence = 0
        do {
            val end = minOf(offset + maxPayloadBytes, payload.size)
            val chunk = payload.copyOfRange(offset, end)
            frames += BluetoothFrame(
                version = PcBleConstants.bluetoothFrameVersion,
                messageId = messageId,
                sequence = sequence,
                isFinal = end >= payload.size,
                totalBytes = payload.size,
                payloadBase64 = Base64.getEncoder().encodeToString(chunk)
            )
            offset += maxPayloadBytes
            sequence++
        } while (offset < payload.size)
        return frames
    }

    fun encode(frame: BluetoothFrame): ByteArray {
        return JSONObject()
            .put("version", frame.version)
            .put("messageId", frame.messageId)
            .put("sequence", frame.sequence)
            .put("isFinal", frame.isFinal)
            .put("totalBytes", frame.totalBytes)
            .put("payloadBase64", frame.payloadBase64)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(bytes: ByteArray): BluetoothFrame? {
        return runCatching {
            val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
            BluetoothFrame(
                version = json.getInt("version"),
                messageId = json.getString("messageId"),
                sequence = json.getInt("sequence"),
                isFinal = json.getBoolean("isFinal"),
                totalBytes = json.getInt("totalBytes"),
                payloadBase64 = json.getString("payloadBase64")
            )
        }.getOrNull()
    }

    fun validate(
        frame: BluetoothFrame,
        maxMessageBytes: Int = PcBleConstants.defaultBluetoothMaxMessageBytes
    ): BluetoothFrameRejectReason? {
        if (frame.totalBytes > maxMessageBytes) return BluetoothFrameRejectReason.MessageTooLarge
        if (
            frame.version != PcBleConstants.bluetoothFrameVersion ||
            frame.messageId.isBlank() ||
            frame.sequence < 0 ||
            frame.totalBytes < 0 ||
            !isValidBase64(frame.payloadBase64)
        ) {
            return BluetoothFrameRejectReason.InvalidFrame
        }
        return null
    }

    fun payloadBytes(frame: BluetoothFrame): ByteArray? {
        return runCatching { Base64.getDecoder().decode(frame.payloadBase64) }.getOrNull()
    }

    private fun isValidBase64(value: String): Boolean {
        if (value.isEmpty()) return true
        if (value.length % 4 != 0) return false
        return value.matches(Regex("^[A-Za-z0-9+/]+={0,2}$"))
    }
}

class BluetoothFrameReassembler(
    private val maxMessageBytes: Int = PcBleConstants.defaultBluetoothMaxMessageBytes,
    private val partialTimeoutMs: Long = PcBleConstants.defaultBluetoothPartialTimeoutMs,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private data class PartialMessage(
        val totalBytes: Int,
        val createdAt: Long,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf()
    )

    private val partialMessages = mutableMapOf<String, PartialMessage>()

    fun accept(frame: BluetoothFrame): BluetoothFrameReassemblyResult {
        BluetoothFrameCodec.validate(frame, maxMessageBytes)?.let {
            return BluetoothFrameReassemblyResult.Rejected(it)
        }
        clearExpired()

        val partial = partialMessages[frame.messageId] ?: PartialMessage(frame.totalBytes, now())
        if (partial.totalBytes != frame.totalBytes) {
            partialMessages.remove(frame.messageId)
            return BluetoothFrameReassemblyResult.Rejected(BluetoothFrameRejectReason.InvalidFrame)
        }

        if (!partial.chunks.containsKey(frame.sequence)) {
            val payloadBytes = BluetoothFrameCodec.payloadBytes(frame)
                ?: return BluetoothFrameReassemblyResult.Rejected(BluetoothFrameRejectReason.InvalidFrame)
            partial.chunks[frame.sequence] = payloadBytes
        }
        partialMessages[frame.messageId] = partial

        if (!frame.isFinal) return BluetoothFrameReassemblyResult.Incomplete

        val chunks = mutableListOf<ByteArray>()
        var totalBytes = 0
        var sequence = 0
        while (partial.chunks.containsKey(sequence)) {
            val chunk = partial.chunks.getValue(sequence)
            chunks += chunk
            totalBytes += chunk.size
            if (totalBytes > partial.totalBytes) {
                partialMessages.remove(frame.messageId)
                return BluetoothFrameReassemblyResult.Rejected(BluetoothFrameRejectReason.InvalidFrame)
            }
            sequence++
        }
        if (totalBytes != partial.totalBytes) return BluetoothFrameReassemblyResult.Incomplete

        partialMessages.remove(frame.messageId)
        val bytes = ByteArray(totalBytes)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }
        return BluetoothFrameReassemblyResult.Complete(String(bytes, StandardCharsets.UTF_8))
    }

    fun clearExpired(): Int {
        val deadline = now() - partialTimeoutMs
        val expired = partialMessages.filterValues { it.createdAt <= deadline }.keys
        expired.forEach(partialMessages::remove)
        return expired.size
    }
}
