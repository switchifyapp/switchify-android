package com.enaboapps.switchify.pc.protocol

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PcCanonical {
    fun stableStringify(value: Any?): String = PcJson.stringify(value, sortKeys = true, escapeSlashes = true)

    fun authProof(
        id: String,
        deviceId: String,
        timestamp: Long,
        type: String,
        payload: PcJsonObject,
        token: String,
        responseMode: PcResponseMode = PcResponseMode.Ack
    ): String {
        val canonical = listOf(
            PcProtocolConstants.PROTOCOL_VERSION,
            id,
            deviceId,
            timestamp,
            type,
            stableStringify(payload),
            responseMode.protocolValue
        ).joinToString("\n")
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
    }

    private const val HMAC_ALGORITHM = "HmacSHA256"
}
