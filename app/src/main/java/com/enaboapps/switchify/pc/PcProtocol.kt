package com.enaboapps.switchify.pc

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed class PcProtocolResponse {
    data class Ack(val id: String) : PcProtocolResponse()
    data class PairingComplete(val id: String, val desktopId: String, val deviceId: String, val token: String) : PcProtocolResponse()
    data class PointerProfile(val id: String, val profile: PcPointerMovementProfile) : PcProtocolResponse()
    data class Error(val id: String?, val code: String, val message: String) : PcProtocolResponse()
    data object Invalid : PcProtocolResponse()
}

enum class PcCommandResponseMode(val protocolValue: String) {
    Ack("ack"),
    None("none")
}

/**
 * Switchify PC protocol message builder/parser.
 *
 * ## Threat model (protocol v1)
 *
 * The active connector is responsible for transport security. Consequences:
 *
 * - The pairing token is delivered in cleartext in `pairing.complete`. An
 *   attacker who can read transport traffic during pairing can capture the
 *   token and control the PC. After pairing the token is never sent again;
 *   commands carry an HMAC-SHA256 proof ([authProof]) over a canonical form of
 *   the message, so a passive observer of normal traffic cannot forge or
 *   tamper with commands.
 * - The 6-digit verification code shown during pairing is derived from the
 *   desktop ID, device ID, and request nonce, all of which travel in
 *   cleartext. It protects the user from approving the wrong device's
 *   request; it does not protect against an active man-in-the-middle.
 * - The user must explicitly approve every pairing on the PC, so remote
 *   attackers cannot pair silently; the exposure window is the pairing
 *   exchange itself on a hostile LAN.
 *
 * This trade-off is accepted for v1. A future protocol version should encrypt
 * the pairing exchange with a key derived from an out-of-band secret entered by
 * the user.
 */
object PcProtocol {
    fun pairingRequest(
        id: String,
        deviceId: String,
        deviceName: String,
        desktopId: String,
        requestNonce: String
    ): String {
        return JSONObject()
            .put("version", PC_PROTOCOL_VERSION)
            .put("id", id)
            .put("type", "pairing.request")
            .put(
                "payload",
                JSONObject()
                    .put("deviceId", deviceId)
                    .put("deviceName", deviceName)
                    .put("desktopId", desktopId)
                    .put("requestNonce", requestNonce)
            )
            .toString()
    }

    fun authenticatedPing(id: String, deviceId: String, token: String, timestamp: Long): String {
        val payload = JSONObject()
        return authenticatedCommand(
            id = id,
            deviceId = deviceId,
            token = token,
            timestamp = timestamp,
            type = "connection.ping",
            payload = payload
        )
    }

    fun pointerProfile(id: String, deviceId: String, token: String, timestamp: Long): String {
        return authenticatedCommand(
            id = id,
            deviceId = deviceId,
            token = token,
            timestamp = timestamp,
            type = "pointer.profile",
            payload = JSONObject()
        )
    }

    fun authenticatedCommand(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        type: String,
        payload: JSONObject,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        val message = JSONObject()
            .put("version", PC_PROTOCOL_VERSION)
            .put("id", id)
            .put("deviceId", deviceId)
            .put("timestamp", timestamp)
            .put("type", type)
            .put("payload", payload)
        if (responseMode != PcCommandResponseMode.Ack) {
            message.put("responseMode", responseMode.protocolValue)
        }
        message.put("auth", authProof(id, deviceId, timestamp, type, payload, token, responseMode))
        return message.toString()
    }

    fun mouseMove(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        dx: Int,
        dy: Int,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "mouse.move",
            JSONObject().put("dx", dx).put("dy", dy),
            responseMode
        )
    }

    fun mouseClick(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        button: String = "left",
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.click", JSONObject().put("button", button), responseMode)
    }

    fun mouseDoubleClick(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        button: String = "left",
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.doubleClick", JSONObject().put("button", button), responseMode)
    }

    fun mouseRightClick(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.rightClick", JSONObject(), responseMode)
    }

    fun mouseScroll(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        dx: Int,
        dy: Int,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.scroll", JSONObject().put("dx", dx).put("dy", dy), responseMode)
    }

    fun mouseDragStart(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        button: String = "left",
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.dragStart", JSONObject().put("button", button), responseMode)
    }

    fun mouseDragEnd(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        button: String = "left",
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.dragEnd", JSONObject().put("button", button), responseMode)
    }

    fun keyboardTypeText(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        text: String,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "keyboard.typeText", JSONObject().put("text", text), responseMode)
    }

    fun keyboardTextStreamOpen(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        streamId: String,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "keyboard.textStream.open",
            JSONObject().put("streamId", streamId),
            responseMode
        )
    }

    fun keyboardTextStreamChar(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        streamId: String,
        seq: Int,
        text: String,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.None
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "keyboard.textStream.char",
            JSONObject().put("streamId", streamId).put("seq", seq).put("text", text),
            responseMode
        )
    }

    fun keyboardTextStreamChunk(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        streamId: String,
        seq: Int,
        text: String,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "keyboard.textStream.chunk",
            JSONObject().put("streamId", streamId).put("seq", seq).put("text", text),
            responseMode
        )
    }

    fun keyboardTextStreamKey(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        streamId: String,
        seq: Int,
        key: PcKeyboardKey,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.None
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "keyboard.textStream.key",
            JSONObject().put("streamId", streamId).put("seq", seq).put("key", key.protocolValue),
            responseMode
        )
    }

    fun keyboardTextStreamClose(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        streamId: String,
        expectedCount: Int,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "keyboard.textStream.close",
            JSONObject().put("streamId", streamId).put("expectedCount", expectedCount),
            responseMode
        )
    }

    fun keyboardKey(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        key: PcKeyboardKey,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "keyboard.key",
            JSONObject().put("key", key.protocolValue),
            responseMode
        )
    }

    fun keyboardShortcut(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        keys: List<PcKeyboardShortcutKey>,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "keyboard.shortcut",
            JSONObject().put("keys", JSONArray(keys.map { it.protocolValue })),
            responseMode
        )
    }

    fun windowControl(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        action: PcWindowControlAction,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "window.control",
            JSONObject().put("action", action.protocolValue),
            responseMode
        )
    }

    fun parseResponse(raw: String): PcProtocolResponse {
        return runCatching {
            val json = JSONObject(raw)
            when (json.optString("type")) {
                "ack" -> {
                    if (!json.optBoolean("ok") || !json.isNull("error")) PcProtocolResponse.Invalid
                    else json.optString("id").takeIf { it.isNotBlank() }?.let(PcProtocolResponse::Ack)
                        ?: PcProtocolResponse.Invalid
                }
                "pairing.complete" -> parsePairingComplete(json)
                "pointer.profile" -> parsePointerProfile(json)
                "error" -> {
                    val error = json.optJSONObject("error") ?: return@runCatching PcProtocolResponse.Invalid
                    PcProtocolResponse.Error(
                        id = json.optString("id").takeIf { it.isNotBlank() },
                        code = error.optString("code"),
                        message = error.optString("message")
                    )
                }
                else -> PcProtocolResponse.Invalid
            }
        }.getOrDefault(PcProtocolResponse.Invalid)
    }

    fun validatePairingComplete(
        response: PcProtocolResponse.PairingComplete,
        expectedDesktopId: String,
        expectedDeviceId: String
    ): Boolean {
        return response.desktopId == expectedDesktopId &&
                response.deviceId == expectedDeviceId &&
                response.token.isNotBlank()
    }

    fun errorReason(message: String): PcErrorReason {
        return when (message) {
            "pairing_rejected" -> PcErrorReason.PairingRejected
            "pairing_request_expired" -> PcErrorReason.PairingRequestExpired
            else -> PcErrorReason.Failed
        }
    }

    fun userMessageForError(message: String): String {
        return when (errorReason(message)) {
            PcErrorReason.PairingRejected -> "Request rejected."
            PcErrorReason.PairingRequestExpired -> "Request expired. Try again."
            else -> "Could not connect to this PC."
        }
    }

    fun authProof(
        id: String,
        deviceId: String,
        timestamp: Long,
        type: String,
        payload: JSONObject,
        token: String,
        responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
    ): String {
        val canonical = listOf(
            PC_PROTOCOL_VERSION,
            id,
            deviceId,
            timestamp,
            type,
            stableStringify(payload),
            responseMode.protocolValue
        ).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)))
    }

    fun stableStringify(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = value.keys().asSequence().toList().sorted()
            keys.joinToString(separator = ",", prefix = "{", postfix = "}") { key ->
                JSONObject.quote(key) + ":" + stableStringify(value.get(key))
            }
        }
        is JSONArray -> {
            (0 until value.length()).joinToString(separator = ",", prefix = "[", postfix = "]") {
                stableStringify(value.get(it))
            }
        }
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun parsePairingComplete(json: JSONObject): PcProtocolResponse {
        if (!json.optBoolean("ok") || !json.isNull("error")) return PcProtocolResponse.Invalid
        val payload = json.optJSONObject("payload") ?: return PcProtocolResponse.Invalid
        val id = json.optString("id")
        val desktopId = payload.optString("desktopId")
        val deviceId = payload.optString("deviceId")
        val token = payload.optString("token")
        if (id.isBlank() || desktopId.isBlank() || deviceId.isBlank() || token.isBlank()) {
            return PcProtocolResponse.Invalid
        }
        return PcProtocolResponse.PairingComplete(id, desktopId, deviceId, token)
    }

    private fun parsePointerProfile(json: JSONObject): PcProtocolResponse {
        if (!json.optBoolean("ok") || !json.isNull("error")) return PcProtocolResponse.Invalid
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return PcProtocolResponse.Invalid
        val payload = json.optJSONObject("payload") ?: return PcProtocolResponse.Invalid
        val displayId = payload.optString("displayId").takeIf { it.isNotBlank() } ?: return PcProtocolResponse.Invalid
        val scaleFactor = payload.optDouble("scaleFactor")
        val boundsJson = payload.optJSONObject("bounds") ?: return PcProtocolResponse.Invalid
        val maxDelta = payload.optInt("maxDelta")
        val deltasJson = payload.optJSONObject("recommendedDeltas") ?: return PcProtocolResponse.Invalid
        val capabilitiesJson = payload.optJSONObject("capabilities")
        if (capabilitiesJson != null && capabilitiesJson.has("noAckMouseMove") && capabilitiesJson.opt("noAckMouseMove") !is Boolean) {
            return PcProtocolResponse.Invalid
        }
        val noAckCommands = parseNoAckCommands(capabilitiesJson) ?: return PcProtocolResponse.Invalid
        val supportedCommands = parseSupportedCommands(capabilitiesJson) ?: return PcProtocolResponse.Invalid
        val capabilities = PcPointerCapabilities(
            noAckMouseMove = capabilitiesJson?.optBoolean("noAckMouseMove", false) ?: false,
            noAckCommands = noAckCommands,
            supportedCommands = supportedCommands
        )
        val bounds = PcPointerBounds(
            x = boundsJson.optInt("x"),
            y = boundsJson.optInt("y"),
            width = boundsJson.optInt("width"),
            height = boundsJson.optInt("height")
        )
        val deltas = PcPointerDeltas(
            small = deltasJson.optInt("small"),
            medium = deltasJson.optInt("medium"),
            large = deltasJson.optInt("large")
        )
        if (
            !scaleFactor.isFinite() ||
            scaleFactor <= 0.0 ||
            bounds.width <= 0 ||
            bounds.height <= 0 ||
            maxDelta <= 0 ||
            deltas.small <= 0 ||
            deltas.medium <= 0 ||
            deltas.large <= 0
        ) {
            return PcProtocolResponse.Invalid
        }
        return PcProtocolResponse.PointerProfile(
            id = id,
            profile = PcPointerMovementProfile(
                displayId = displayId,
                scaleFactor = scaleFactor,
                bounds = bounds,
                maxDelta = maxDelta,
                recommendedDeltas = deltas,
                capabilities = capabilities
            )
        )
    }

    private fun parseNoAckCommands(capabilitiesJson: JSONObject?): Set<String>? {
        if (capabilitiesJson == null || !capabilitiesJson.has("noAckCommands")) return emptySet()
        val commandsJson = capabilitiesJson.opt("noAckCommands")
        if (commandsJson !is JSONArray) return null

        val commands = mutableSetOf<String>()
        for (index in 0 until commandsJson.length()) {
            val command = commandsJson.opt(index)
            if (command !is String) return null
            if (command in NO_ACK_CONTROL_COMMAND_TYPES) {
                commands += command
            }
        }
        return commands
    }

    private fun parseSupportedCommands(capabilitiesJson: JSONObject?): Set<String>? {
        if (capabilitiesJson == null || !capabilitiesJson.has("supportedCommands")) return emptySet()
        val commandsJson = capabilitiesJson.opt("supportedCommands")
        if (commandsJson !is JSONArray) return null

        val commands = mutableSetOf<String>()
        for (index in 0 until commandsJson.length()) {
            val command = commandsJson.opt(index)
            if (command !is String) return null
            if (command in CONTROL_COMMAND_TYPES) {
                commands += command
            }
        }
        return commands
    }

    private val NO_ACK_CONTROL_COMMAND_TYPES = setOf(
        "mouse.move",
        "mouse.click",
        "mouse.doubleClick",
        "mouse.rightClick",
        "mouse.scroll",
        "mouse.dragStart",
        "mouse.dragEnd",
        "keyboard.key",
        "keyboard.shortcut",
        "keyboard.typeText",
        "keyboard.textStream.char",
        "keyboard.textStream.key",
        "media.control",
        "window.control"
    )

    private val CONTROL_COMMAND_TYPES = NO_ACK_CONTROL_COMMAND_TYPES + setOf(
        "connection.ping",
        "connection.disconnecting",
        "pointer.profile",
        "keyboard.textStream.open",
        "keyboard.textStream.chunk",
        "keyboard.textStream.close"
    )
}
