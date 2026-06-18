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
        payload: JSONObject
    ): String {
        val message = JSONObject()
            .put("version", PC_PROTOCOL_VERSION)
            .put("id", id)
            .put("deviceId", deviceId)
            .put("timestamp", timestamp)
            .put("type", type)
            .put("payload", payload)
        message.put("auth", authProof(id, deviceId, timestamp, type, payload, token))
        return message.toString()
    }

    fun mouseMove(id: String, deviceId: String, token: String, timestamp: Long, dx: Int, dy: Int): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.move", JSONObject().put("dx", dx).put("dy", dy))
    }

    fun mouseClick(id: String, deviceId: String, token: String, timestamp: Long, button: String = "left"): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.click", JSONObject().put("button", button))
    }

    fun mouseDoubleClick(id: String, deviceId: String, token: String, timestamp: Long, button: String = "left"): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.doubleClick", JSONObject().put("button", button))
    }

    fun mouseRightClick(id: String, deviceId: String, token: String, timestamp: Long): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.rightClick", JSONObject())
    }

    fun mouseScroll(id: String, deviceId: String, token: String, timestamp: Long, dx: Int, dy: Int): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.scroll", JSONObject().put("dx", dx).put("dy", dy))
    }

    fun mouseDragStart(id: String, deviceId: String, token: String, timestamp: Long, button: String = "left"): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.dragStart", JSONObject().put("button", button))
    }

    fun mouseDragEnd(id: String, deviceId: String, token: String, timestamp: Long, button: String = "left"): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "mouse.dragEnd", JSONObject().put("button", button))
    }

    fun keyboardTypeText(id: String, deviceId: String, token: String, timestamp: Long, text: String): String {
        return authenticatedCommand(id, deviceId, token, timestamp, "keyboard.typeText", JSONObject().put("text", text))
    }

    fun keyboardKey(id: String, deviceId: String, token: String, timestamp: Long, key: PcKeyboardKey): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "keyboard.key",
            JSONObject().put("key", key.protocolValue)
        )
    }

    fun windowControl(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        action: PcWindowControlAction
    ): String {
        return authenticatedCommand(
            id,
            deviceId,
            token,
            timestamp,
            "window.control",
            JSONObject().put("action", action.protocolValue)
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

    fun authProof(id: String, deviceId: String, timestamp: Long, type: String, payload: JSONObject, token: String): String {
        val canonical = listOf(PC_PROTOCOL_VERSION, id, deviceId, timestamp, type, stableStringify(payload)).joinToString("\n")
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
                recommendedDeltas = deltas
            )
        )
    }
}
