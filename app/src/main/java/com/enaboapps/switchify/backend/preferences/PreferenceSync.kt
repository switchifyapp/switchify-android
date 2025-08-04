package com.enaboapps.switchify.backend.preferences

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.enaboapps.switchify.backend.supabase.SupabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages synchronization between local SharedPreferences and Supabase PostgreSQL.
 * Handles automatic syncing of user preferences across devices with support for
 * real-time updates and type-safe storage.
 */
class PreferenceSync private constructor() {
    private var sharedPreferences: SharedPreferences? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    // TODO: Implement real-time listener for Supabase
    private var isInitialized = false

    companion object {
        private const val TAG = "PreferenceSync"
        private const val COLLECTION_USER_SETTINGS = "user-settings"
        private const val DOCUMENT_PREFERENCES = "preferences"
        private const val COLLECTION_USERS = "users"
        private val BLACKLISTED_KEYS = setOf(
            PreferenceManager.Keys.PREFERENCE_KEY_PRO,
            PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE
        )

        @Volatile
        private var instance: PreferenceSync? = null

        fun getInstance(): PreferenceSync {
            return instance ?: synchronized(this) {
                instance ?: PreferenceSync().also { instance = it }
            }
        }
    }

    fun initialize(preferences: SharedPreferences) {
        sharedPreferences = preferences
        isInitialized = true
    }

    private fun checkInitialized(): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "PreferenceSync not initialized. Call initialize() first.")
            return false
        }
        return true
    }


    /**
     * Uploads current SharedPreferences to Supabase.
     */
    fun uploadSettingsToSupabase() {
        if (!checkInitialized()) return

        coroutineScope.launch {
            try {
                val userSettings = getAllPreferences() ?: return@launch
                
                val result = SupabaseManager.getInstance().saveUserPreferences(userSettings)
                result.fold(
                    onSuccess = {
                        Log.i(TAG, "Settings uploaded successfully")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error uploading settings", e)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading settings", e)
            }
        }
    }

    /**
     * Downloads and applies settings from Supabase to SharedPreferences.
     */
    fun retrieveSettingsFromSupabase() {
        if (!checkInitialized()) return

        coroutineScope.launch {
            try {
                val result = SupabaseManager.getInstance().getUserPreferences()
                result.fold(
                    onSuccess = { typedSettings ->
                        if (typedSettings.isNotEmpty()) {
                            applySettings(typedSettings)
                            Log.i(TAG, "Settings retrieved and applied successfully")
                        } else {
                            Log.w(TAG, "No settings found")
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error retrieving settings", e)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving settings", e)
            }
        }
    }

    /**
     * Gets all non-blacklisted preferences with supported types.
     */
    private fun getAllPreferences(): Map<String, Any>? {
        val prefs = sharedPreferences ?: return null
        return prefs.all.mapNotNull { (key, value) ->
            if (!BLACKLISTED_KEYS.contains(key) && value != null) {
                when (value) {
                    is String, is Boolean, is Int, is Long, is Float -> key to value
                    else -> {
                        Log.w(TAG, "Unsupported type for key: $key, value: $value")
                        null
                    }
                }
            } else null
        }.toMap()
    }


    /**
     * Applies settings to SharedPreferences with type conversion.
     */
    private fun applySettings(settings: Map<String, Any>) {
        val prefs = sharedPreferences ?: return
        prefs.edit {
            settings.forEach { (key, value) ->
                if (!BLACKLISTED_KEYS.contains(key)) {
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Int -> putInt(key, value)
                        else -> Log.w(TAG, "Unsupported type for key: $key, value: $value")
                    }
                }
            }
        }
    }
}