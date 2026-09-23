package com.enaboapps.switchify.service.scanning

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.utils.Resources

class ScanHighlightStyle(context: Context) {
    private val preferenceManager = PreferenceManager(context)

    companion object {
        private val BORDER = Type("border")
        private val FILL = Type("fill")
        private val SPOTLIGHT = Type("spotlight")
        val ALL = listOf(BORDER, FILL, SPOTLIGHT)

        internal fun legacyTypeToSave(current: Type, next: Type): Type? =
            (if (next == SPOTLIGHT) current else next).takeIf { it == BORDER || it == FILL }
    }

    data class Type(val id: String)

    fun getType(): Type {
        val type = preferenceManager.getStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_SCAN_HIGHLIGHT_TYPE,
            BORDER.id
        )
        return Type(type)
    }

    fun setType(type: Type) {
        val legacyType = legacyTypeToSave(getType(), type)
        if (legacyType != null) {
            preferenceManager.setStringValue(PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_LEGACY_TYPE, legacyType.id)
        }
        preferenceManager.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_SCAN_HIGHLIGHT_TYPE,
            type.id
        )
    }

    fun isSpotlight(): Boolean = getType() == SPOTLIGHT

    fun isMovementEnabled(): Boolean = preferenceManager.getBooleanValue(
        PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_MOVEMENT, true)

    fun isCountdownEnabled(): Boolean = preferenceManager.getBooleanValue(
        PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_COUNTDOWN, true)

    fun isLegacyFill(): Boolean = if (isSpotlight()) {
        preferenceManager.getStringValue(PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_LEGACY_TYPE, BORDER.id) == FILL.id
    } else isFill()

    fun isBorder(): Boolean {
        return getType() == BORDER
    }

    fun isFill(): Boolean {
        return getType() == FILL
    }

    fun getName(type: Type): String {
        return when (type) {
            BORDER -> Resources.getString(R.string.scan_highlight_type_border)
            SPOTLIGHT -> Resources.getString(R.string.scan_highlight_type_spotlight)
            FILL -> Resources.getString(R.string.scan_highlight_type_fill)
            else -> ""
        }
    }

    fun getDescription(type: Type): String {
        return when (type) {
            BORDER -> Resources.getString(R.string.scan_highlight_type_border_desc)
            SPOTLIGHT -> Resources.getString(R.string.scan_highlight_type_spotlight_desc)
            FILL -> Resources.getString(R.string.scan_highlight_type_fill_desc)
            else -> ""
        }
    }
}
