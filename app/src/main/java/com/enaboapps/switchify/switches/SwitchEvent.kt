package com.enaboapps.switchify.switches

import com.google.gson.annotations.SerializedName

const val SWITCH_EVENT_TYPE_EXTERNAL = "external"
const val SWITCH_EVENT_TYPE_CAMERA = "camera"

data class SwitchEvent(
    @SerializedName("type") var type: String = SWITCH_EVENT_TYPE_EXTERNAL,
    @SerializedName("name") var name: String,
    @SerializedName("code") var code: String,
    @SerializedName("press_action") var pressAction: SwitchAction,
    @SerializedName("hold_actions") var holdActions: List<SwitchAction>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to (type.takeIf { it.isNotEmpty() } ?: SWITCH_EVENT_TYPE_EXTERNAL),
        "name" to name,
        "code" to code,
        "press_action" to pressAction.toMap(),
        "hold_actions" to holdActions.map { it.toMap() }
    )

    fun log() {
        println(
            "SwitchEvent: $type, $name, $code, ${pressAction.id}, ${
                holdActions.joinToString(
                    separator = ";"
                ) { it.id.toString() }
            }"
        )
    }

    fun containsAction(actionId: Int): Boolean {
        return pressAction.id == actionId || holdActions.any { it.id == actionId }
    }

    /**
     * Fills in fields that Gson leaves null when they are missing from older JSON
     * (for example switch files written before hold_actions or type existed).
     * Returns null when the event has no usable press action.
     */
    fun sanitized(): SwitchEvent? {
        val safeType: String? = type
        val safeName: String? = name
        val safeCode: String? = code
        val safePressAction: SwitchAction? = pressAction
        val safeHoldActions: List<SwitchAction>? = holdActions
        if (safePressAction == null || safeCode == null) return null
        return SwitchEvent(
            type = safeType?.takeIf { it.isNotEmpty() } ?: SWITCH_EVENT_TYPE_EXTERNAL,
            name = safeName ?: safeCode,
            code = safeCode,
            pressAction = safePressAction,
            holdActions = safeHoldActions?.filterNotNull() ?: emptyList()
        )
    }
}