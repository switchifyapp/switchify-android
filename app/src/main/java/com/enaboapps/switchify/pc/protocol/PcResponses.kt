package com.enaboapps.switchify.pc.protocol

import org.json.JSONArray
import org.json.JSONObject

enum class PcPlatform(val protocolValue: String) {
    Windows("windows"),
    MacOs("macos");

    companion object {
        fun fromProtocol(value: Any?): PcPlatform? = entries.firstOrNull { it.protocolValue == value }
    }
}

enum class PcResponseTransport(val protocolValue: String) {
    ReadV1("read-v1")
}

data class PcStatus(
    val desktopId: String,
    val displayName: String,
    val platform: PcPlatform?,
    val responseTransport: PcResponseTransport? = null
)

data class PcBounds(val x: Double, val y: Double, val width: Double, val height: Double)

data class PcRecommendedDeltas(val small: Double, val medium: Double, val large: Double)

data class PcMouseRepeatCapability(
    val supported: Boolean,
    val enabled: Boolean,
    val intervalMs: Double,
    val minIntervalMs: Double,
    val maxIntervalMs: Double
)

data class PcKeyRepeatCapability(
    val supported: Boolean,
    val enabled: Boolean,
    val intervalMs: Double,
    val initialDelayMs: Double,
    val minIntervalMs: Double,
    val maxIntervalMs: Double,
    val repeatableKeys: List<String>
)

data class PcPointerSpeedCapability(
    val supported: Boolean,
    val setSupported: Boolean,
    val scalePercent: Double,
    val minScalePercent: Double,
    val maxScalePercent: Double,
    val stepPercent: Double,
    val baseMoveDelta: Double,
    val effectiveMoveDelta: Double
)

data class PcDisplayNavigationCapability(val supported: Boolean, val displayCount: Double)

data class PcCapabilities(
    val noAckMouseMove: Boolean,
    val switchScanning: Boolean,
    val noAckCommands: List<String>,
    val supportedCommands: List<String>,
    val mouseRepeat: PcMouseRepeatCapability,
    val keyRepeat: PcKeyRepeatCapability,
    val pointerSpeed: PcPointerSpeedCapability,
    val displayNavigation: PcDisplayNavigationCapability
) {
    fun supportsNoAck(type: String): Boolean = type in noAckCommands

    fun supports(type: String): Boolean = type in supportedCommands

    fun noAckResponseModeFor(type: String): PcResponseMode =
        if (supportsNoAck(type)) PcResponseMode.None else PcResponseMode.Ack
}

data class PcPointerProfile(
    val displayId: String,
    val scaleFactor: Double,
    val bounds: PcBounds,
    val maxDelta: Double,
    val recommendedDeltas: PcRecommendedDeltas,
    val capabilities: PcCapabilities
)

enum class PcSwitchBindingBehavior(val protocolValue: String) {
    Stateful("stateful"),
    Pulse("pulse"),
    Unassigned("unassigned")
}

enum class PcSwitchProfileKind(val protocolValue: String) {
    Grid3("grid3"),
    Mapped("mapped"),
    Scanning("scanning")
}

data class PcSwitchBinding(val switchId: Int, val label: String, val behavior: PcSwitchBindingBehavior)

data class PcSwitchProfile(
    val id: String,
    val version: Long,
    val name: String,
    val kind: PcSwitchProfileKind,
    val bindings: List<PcSwitchBinding>
)

data class PcSwitchProfileCatalog(val catalogRevision: Long, val profiles: List<PcSwitchProfile>)

sealed class PcResponse {
    data class Ack(val id: String) : PcResponse()

    data class PairingComplete(val id: String, val desktopId: String, val deviceId: String, val token: String) :
        PcResponse() {
        override fun toString(): String = "PairingComplete(id=$id, desktopId=$desktopId, deviceId=$deviceId)"
    }

    data class PointerProfile(val id: String, val profile: PcPointerProfile) : PcResponse()

    data class SwitchProfileCatalog(val id: String, val catalog: PcSwitchProfileCatalog) : PcResponse()

    data class Error(val id: String?, val code: String, val message: String) : PcResponse()

    data object Invalid : PcResponse()

    val responseId: String?
        get() = when (this) {
            is Ack -> id
            is PairingComplete -> id
            is PointerProfile -> id
            is SwitchProfileCatalog -> id
            is Error -> id
            Invalid -> null
        }
}

object PcResponses {
    fun parseStatus(raw: String): PcStatus? = try {
        val value = JSONObject(raw)
        val protocolVersion = number(value.opt("protocolVersion"))
        val desktopId = string(value.opt("desktopId"))
        val responseTransport = value.opt("responseTransport")
        when {
            protocolVersion != PcProtocolConstants.PROTOCOL_VERSION.toDouble() -> null
            desktopId == null -> null
            value.has("responseTransport") && responseTransport != PcResponseTransport.ReadV1.protocolValue -> null
            else -> PcStatus(
                desktopId = desktopId,
                displayName = string(value.opt("displayName"))?.trim()?.ifEmpty { null }
                    ?: PcProtocolConstants.DEFAULT_PRODUCT_NAME,
                platform = PcPlatform.fromProtocol(value.opt("platform")),
                responseTransport = if (responseTransport == null) null else PcResponseTransport.ReadV1
            )
        }
    } catch (_: Exception) {
        null
    }

    fun parseResponse(raw: String): PcResponse = try {
        parse(JSONObject(raw))
    } catch (_: Exception) {
        PcResponse.Invalid
    }

    private fun parse(value: JSONObject): PcResponse {
        val id = string(value.opt("id"))
        val type = value.opt("type")
        if (type == "error") {
            val error = value.opt("error") as? JSONObject ?: return PcResponse.Invalid
            val code = error.opt("code") as? String ?: return PcResponse.Invalid
            val message = error.opt("message") as? String ?: return PcResponse.Invalid
            return PcResponse.Error(id, code, message)
        }
        val error = value.opt("error")
        if (id == null || value.opt("ok") != true || (error != null && error != JSONObject.NULL)) return PcResponse.Invalid
        if (type == "ack") return PcResponse.Ack(id)
        val payload = value.opt("payload") as? JSONObject ?: return PcResponse.Invalid
        return when (type) {
            "pairing.complete" -> {
                val desktopId = string(payload.opt("desktopId"))
                val deviceId = string(payload.opt("deviceId"))
                val token = string(payload.opt("token"))
                if (desktopId != null && deviceId != null && token != null) {
                    PcResponse.PairingComplete(id, desktopId, deviceId, token)
                } else {
                    PcResponse.Invalid
                }
            }
            PcCommandTypes.POINTER_PROFILE ->
                parsePointerProfile(payload)?.let { PcResponse.PointerProfile(id, it) } ?: PcResponse.Invalid
            PcCommandTypes.SWITCH_PROFILE_LIST ->
                parseSwitchProfileCatalog(payload)?.let { PcResponse.SwitchProfileCatalog(id, it) } ?: PcResponse.Invalid
            else -> PcResponse.Invalid
        }
    }

    private fun parseSwitchProfileCatalog(payload: JSONObject): PcSwitchProfileCatalog? {
        val revision = integer(payload.opt("catalogRevision"))?.takeIf { it >= 0L } ?: return null
        val entries = payload.opt("profiles") as? JSONArray ?: return null
        if (entries.length() > MAX_SWITCH_PROFILES) return null
        val profiles = (0 until entries.length()).map { index ->
            val profile = entries.opt(index) as? JSONObject ?: return null
            val id = string(profile.opt("id")) ?: return null
            val version = integer(profile.opt("version"))?.takeIf { it != 0L } ?: return null
            val name = string(profile.opt("name")) ?: return null
            val kind = PcSwitchProfileKind.entries.firstOrNull { it.protocolValue == profile.opt("kind") } ?: return null
            val bindingEntries = profile.opt("bindings") as? JSONArray ?: return null
            if (bindingEntries.length() > MAX_SWITCH_BINDINGS) return null
            val bindings = (0 until bindingEntries.length()).map { bindingIndex ->
                val binding = bindingEntries.opt(bindingIndex) as? JSONObject ?: return null
                val switchId = integer(binding.opt("switchId"))?.takeIf { it in 1L..MAX_SWITCH_BINDINGS }?.toInt() ?: return null
                val label = string(binding.opt("label")) ?: return null
                val behavior = PcSwitchBindingBehavior.entries.firstOrNull { it.protocolValue == binding.opt("behavior") }
                    ?: return null
                PcSwitchBinding(switchId, label, behavior)
            }
            PcSwitchProfile(id, version, name, kind, bindings)
        }
        return PcSwitchProfileCatalog(revision, profiles)
    }

    private fun parsePointerProfile(payload: JSONObject): PcPointerProfile? {
        val bounds = payload.opt("bounds") as? JSONObject ?: return null
        val deltas = payload.opt("recommendedDeltas") as? JSONObject ?: return null
        val capabilities = payload.opt("capabilities") as? JSONObject ?: JSONObject()
        val repeat = capabilities.opt("mouseRepeat") as? JSONObject ?: JSONObject()
        val keyRepeat = capabilities.opt("keyRepeat") as? JSONObject ?: JSONObject()
        val speed = capabilities.opt("pointerSpeed") as? JSONObject ?: JSONObject()
        val displays = capabilities.opt("displayNavigation") as? JSONObject ?: JSONObject()
        val displayId = string(payload.opt("displayId")) ?: return null
        val scaleFactor = number(payload.opt("scaleFactor")) ?: return null
        val maxDelta = number(payload.opt("maxDelta")) ?: return null
        val parsedBounds = PcBounds(
            x = number(bounds.opt("x")) ?: return null,
            y = number(bounds.opt("y")) ?: return null,
            width = number(bounds.opt("width")) ?: return null,
            height = number(bounds.opt("height")) ?: return null
        )
        val parsedDeltas = PcRecommendedDeltas(
            small = number(deltas.opt("small")) ?: return null,
            medium = number(deltas.opt("medium")) ?: return null,
            large = number(deltas.opt("large")) ?: return null
        )
        fun JSONObject.bool(key: String) = opt(key) == true
        fun JSONObject.numeric(key: String, fallback: Double) = number(opt(key)) ?: fallback
        return PcPointerProfile(
            displayId = displayId,
            scaleFactor = scaleFactor,
            bounds = parsedBounds,
            maxDelta = maxDelta,
            recommendedDeltas = parsedDeltas,
            capabilities = PcCapabilities(
                noAckMouseMove = capabilities.bool("noAckMouseMove"),
                switchScanning = capabilities.bool("switchScanning"),
                noAckCommands = strings(capabilities.opt("noAckCommands")),
                supportedCommands = strings(capabilities.opt("supportedCommands")),
                mouseRepeat = PcMouseRepeatCapability(
                    supported = repeat.bool("supported"),
                    enabled = repeat.bool("enabled"),
                    intervalMs = repeat.numeric("intervalMs", 250.0),
                    minIntervalMs = repeat.numeric("minIntervalMs", 100.0),
                    maxIntervalMs = repeat.numeric("maxIntervalMs", 2000.0)
                ),
                keyRepeat = PcKeyRepeatCapability(
                    supported = keyRepeat.bool("supported"),
                    enabled = keyRepeat.bool("enabled"),
                    intervalMs = keyRepeat.numeric("intervalMs", 250.0),
                    initialDelayMs = keyRepeat.numeric("initialDelayMs", 500.0),
                    minIntervalMs = keyRepeat.numeric("minIntervalMs", 100.0),
                    maxIntervalMs = keyRepeat.numeric("maxIntervalMs", 1000.0),
                    repeatableKeys = strings(keyRepeat.opt("repeatableKeys"))
                ),
                pointerSpeed = PcPointerSpeedCapability(
                    supported = speed.bool("supported"),
                    setSupported = speed.bool("setSupported"),
                    scalePercent = speed.numeric("scalePercent", 100.0),
                    minScalePercent = speed.numeric("minScalePercent", 5.0),
                    maxScalePercent = speed.numeric("maxScalePercent", 225.0),
                    stepPercent = speed.numeric("stepPercent", 5.0),
                    baseMoveDelta = speed.numeric("baseMoveDelta", 128.0),
                    effectiveMoveDelta = speed.numeric("effectiveMoveDelta", 128.0)
                ),
                displayNavigation = PcDisplayNavigationCapability(
                    supported = displays.bool("supported"),
                    displayCount = displays.numeric("displayCount", 1.0)
                )
            )
        )
    }

    private fun number(value: Any?): Double? = (value as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private fun integer(value: Any?): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Short -> value.toLong()
        is Byte -> value.toLong()
        else -> number(value)?.takeIf { it % 1.0 == 0.0 && it >= Long.MIN_VALUE && it <= Long.MAX_VALUE }?.toLong()
    }

    private fun string(value: Any?): String? = (value as? String)?.takeIf { it.isNotEmpty() }

    private fun strings(value: Any?): List<String> {
        val array = value as? JSONArray ?: return emptyList()
        val items = (0 until array.length()).map { array.opt(it) }
        return if (items.all { it is String }) items.map { it as String } else emptyList()
    }

    private const val MAX_SWITCH_PROFILES = 34
    private const val MAX_SWITCH_BINDINGS = 8
}
