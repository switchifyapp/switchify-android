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
        val ALL = listOf(BORDER, FILL)
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
        preferenceManager.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_SCAN_HIGHLIGHT_TYPE,
            type.id
        )
    }

    fun isBorder(): Boolean {
        return getType() == BORDER
    }

    fun isFill(): Boolean {
        return getType() == FILL
    }

    fun getName(type: Type): String {
        return when (type) {
            BORDER -> Resources.getString(R.string.scan_highlight_type_border)
            FILL -> Resources.getString(R.string.scan_highlight_type_fill)
            else -> ""
        }
    }

    fun getDescription(type: Type): String {
        return when (type) {
            BORDER -> Resources.getString(R.string.scan_highlight_type_border_desc)
            FILL -> Resources.getString(R.string.scan_highlight_type_fill_desc)
            else -> ""
        }
    }
}