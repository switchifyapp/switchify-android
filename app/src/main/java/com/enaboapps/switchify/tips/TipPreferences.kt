package com.enaboapps.switchify.tips

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TipPreferences(context: Context) : TipRegistrationState {

    private val preferences: SharedPreferences = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit { putBoolean(KEY_ENABLED, value) }

    override var registrationMayExist: Boolean
        get() = preferences.getBoolean(KEY_REGISTRATION_MAY_EXIST, false)
        set(value) = preferences.edit { putBoolean(KEY_REGISTRATION_MAY_EXIST, value) }

    override var subscribedAtMillis: Long
        get() = preferences.getLong(KEY_SUBSCRIBED_AT, 0L)
        set(value) = preferences.edit { putLong(KEY_SUBSCRIBED_AT, value) }

    override var subscribedVersionCode: Int
        get() = preferences.getInt(KEY_SUBSCRIBED_VERSION, 0)
        set(value) = preferences.edit { putInt(KEY_SUBSCRIBED_VERSION, value) }

    override var removalStep: Int
        get() = preferences.getInt(KEY_REMOVAL_STEP, TipRegistrationReconciler.REMOVAL_NOT_STARTED)
        set(value) = preferences.edit { putInt(KEY_REMOVAL_STEP, value) }

    fun markShown(tipId: String): Boolean {
        val shown = preferences.getString(KEY_SHOWN_IDS, null).orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotEmpty() }
        val updated = rememberShown(shown, tipId) ?: return false
        preferences.edit { putString(KEY_SHOWN_IDS, updated.joinToString(SEPARATOR)) }
        return true
    }

    fun rememberTip(tip: TipMessage) {
        preferences.edit { putString(KEY_TIPS, TipStore.remember(preferences.getString(KEY_TIPS, null), tip)) }
    }

    fun tip(key: String): TipMessage? = TipStore.find(preferences.getString(KEY_TIPS, null), key)

    fun forgetTips() {
        preferences.edit { remove(KEY_TIPS) }
    }

    companion object {
        const val FILE_NAME = "tip_notifications"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_REGISTRATION_MAY_EXIST = "registration_may_exist"
        private const val KEY_SUBSCRIBED_AT = "subscribed_at"
        private const val KEY_SUBSCRIBED_VERSION = "subscribed_version"
        private const val KEY_REMOVAL_STEP = "removal_step"
        private const val KEY_SHOWN_IDS = "shown_ids"
        private const val KEY_TIPS = "tips"
        private const val SEPARATOR = "\n"
        private const val MAX_SHOWN_IDS = 50

        internal fun rememberShown(shown: List<String>, tipId: String): List<String>? {
            if (tipId in shown) return null
            return (shown + tipId).takeLast(MAX_SHOWN_IDS)
        }
    }
}
