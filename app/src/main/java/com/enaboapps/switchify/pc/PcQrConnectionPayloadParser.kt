package com.enaboapps.switchify.pc

import org.json.JSONObject
import java.net.URI

object PcQrConnectionPayloadParser {
    const val INVALID_MESSAGE = "This is not a valid Switchify PC QR code."

    private const val TYPE = "switchify.pc.connect"
    private const val VERSION = 1
    private const val DEFAULT_SERVICE_NAME = "Switchify PC"
    private const val DEFAULT_PORT = 7347
    private val secretKeys = setOf("token", "pairingToken", "secret", "authToken", "password", "key")

    fun parse(rawValue: String): Result<DiscoveredPc> {
        return runCatching {
            val json = JSONObject(rawValue)
            if (json.hasSecretKey()) throw invalidPayload()
            if (json.optString("type") != TYPE) throw invalidPayload()
            if (json.optInt("version") != VERSION) throw invalidPayload()

            val desktopId = json.optString("desktopId").trim()
            if (desktopId.isBlank()) throw invalidPayload()

            val urls = json.getValidatedUrls()
            val firstUri = URI(urls.first())
            val serviceName = json.optString("displayName").trim().ifBlank { DEFAULT_SERVICE_NAME }

            DiscoveredPc(
                serviceName = serviceName,
                desktopId = desktopId,
                hostAddresses = urls.mapNotNull { url -> URI(url).host?.trim()?.trim('[', ']') }
                    .filter { it.isNotEmpty() }
                    .distinct(),
                port = firstUri.port.takeIf { it > 0 } ?: DEFAULT_PORT,
                websocketUrls = urls
            )
        }.recoverCatching {
            throw invalidPayload()
        }
    }

    private fun JSONObject.hasSecretKey(): Boolean {
        return keys().asSequence().any { key -> key in secretKeys }
    }

    private fun JSONObject.getValidatedUrls(): List<String> {
        val values = optJSONArray("urls") ?: throw invalidPayload()
        val urls = buildList {
            for (index in 0 until values.length()) {
                val url = values.optString(index).trim()
                if (url.isBlank() || url in this) continue
                val uri = runCatching { URI(url) }.getOrElse { throw invalidPayload() }
                val scheme = uri.scheme?.lowercase()
                if (scheme != "ws" && scheme != "wss") throw invalidPayload()
                if (uri.host.isNullOrBlank()) throw invalidPayload()
                add(url)
            }
        }
        if (urls.isEmpty()) throw invalidPayload()
        return urls
    }

    private fun invalidPayload(): IllegalArgumentException = IllegalArgumentException(INVALID_MESSAGE)
}
