package com.enaboapps.switchify.tips

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TipPreferences(context: Context) {

    private val preferences: SharedPreferences = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit { putBoolean(KEY_ENABLED, value) }

    var registrationMayExist: Boolean
        get() = preferences.getBoolean(KEY_REGISTRATION_MAY_EXIST, false)
        set(value) = preferences.edit { putBoolean(KEY_REGISTRATION_MAY_EXIST, value) }

    var subscribed: Boolean
        get() = preferences.getBoolean(KEY_SUBSCRIBED, false)
        set(value) = preferences.edit { putBoolean(KEY_SUBSCRIBED, value) }

    fun markShown(tipId: String): Boolean {
        val shown = preferences.getString(KEY_SHOWN_IDS, null).orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotEmpty() }
        val updated = rememberShown(shown, tipId) ?: return false
        preferences.edit { putString(KEY_SHOWN_IDS, updated.joinToString(SEPARATOR)) }
        return true
    }

    companion object {
        const val FILE_NAME = "tip_notifications"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_REGISTRATION_MAY_EXIST = "registration_may_exist"
        private const val KEY_SUBSCRIBED = "subscribed"
        private const val KEY_SHOWN_IDS = "shown_ids"
        private const val SEPARATOR = "\n"
        private const val MAX_SHOWN_IDS = 50

        internal fun rememberShown(shown: List<String>, tipId: String): List<String>? {
            if (tipId in shown) return null
            return (shown + tipId).takeLast(MAX_SHOWN_IDS)
        }
    }
}
