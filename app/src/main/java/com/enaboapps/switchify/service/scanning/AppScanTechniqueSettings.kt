package com.enaboapps.switchify.service.scanning

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.techniques.AccessTechnique
import org.json.JSONObject

internal class AppScanTechniqueSettings(context: Context) : AppScanTechniquePolicy {
    private val preferences = PreferenceManager(context)

    fun rules(): Map<String, String> = decode(preferences.getStringValue(
        PreferenceManager.PREFERENCE_KEY_APP_SCAN_TECHNIQUES, "{}"
    ))

    override fun techniqueFor(packageName: String): String? = rules()[packageName]

    fun setRule(packageName: String, technique: String?) {
        if (packageName.isBlank() || technique != null && technique !in techniques) return
        val updated = rules().toMutableMap()
        if (technique == null) updated.remove(packageName) else updated[packageName] = technique
        preferences.setStringValue(PreferenceManager.PREFERENCE_KEY_APP_SCAN_TECHNIQUES, JSONObject(updated).toString())
    }

    companion object {
        val techniques = listOf(AccessTechnique.Technique.ITEM_SCAN, AccessTechnique.Technique.POINT_SCAN, AccessTechnique.Technique.RADAR)

        fun decode(value: String): Map<String, String> = runCatching {
            val json = JSONObject(value)
            json.keys().asSequence().mapNotNull { key ->
                val technique = json.optString(key)
                if (key.isNotBlank() && technique in techniques) key to technique else null
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}
