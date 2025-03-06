package com.enaboapps.switchify.backend.preferences

import android.content.SharedPreferences
import android.util.Log
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.data.FirestoreManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages synchronization between local SharedPreferences and Firebase Firestore.
 * Handles automatic syncing of user preferences across devices with support for
 * real-time updates and type-safe storage.
 */
class PreferenceSync private constructor() {
    private var sharedPreferences: SharedPreferences? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var settingsListener: ListenerRegistration? = null
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

    private fun getDocumentPath(): String? {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.w(TAG, "Could not get user ID")
            return null
        }
        return "$COLLECTION_USER_SETTINGS/$DOCUMENT_PREFERENCES/$COLLECTION_USERS/$userId"
    }

    /**
     * Uploads current SharedPreferences to Firestore.
     */
    fun uploadSettingsToFirestore() {
        if (!checkInitialized()) return

        coroutineScope.launch {
            try {
                val userSettings = getAllPreferences() ?: return@launch
                val path = getDocumentPath() ?: return@launch

                FirestoreManager.getInstance().saveDocument(
                    path = path,
                    data = userSettings
                )
                Log.i(TAG, "Settings uploaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading settings", e)
            }
        }
    }

    /**
     * Downloads and applies settings from Firestore to SharedPreferences.
     */
    fun retrieveSettingsFromFirestore() {
        if (!checkInitialized()) return

        coroutineScope.launch {
            try {
                val path = getDocumentPath() ?: return@launch
                val settings = FirestoreManager.getInstance().getDocument(path = path)
                if (settings != null && settings.isNotEmpty()) {
                    applySettings(settings)
                    Log.i(TAG, "Settings retrieved and applied successfully")
                } else {
                    Log.w(TAG, "No settings found in Firestore")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving settings", e)
            }
        }
    }

    /**
     * Sets up real-time listener for remote preference changes.
     */
    fun listenForSettingsChangesOnRemote() {
        if (!checkInitialized()) return

        try {
            settingsListener?.remove()
            val path = getDocumentPath() ?: return

            settingsListener = FirestoreManager.getInstance().listenToDocument(
                path = path,
                onDocument = { settings ->
                    settings?.let { applySettings(it) }
                },
                onError = { e ->
                    Log.e(TAG, "Error in remote settings listener", e)
                }
            )
            Log.i(TAG, "Registered listener for remote settings changes")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up remote settings listener", e)
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
        with(prefs.edit()) {
            settings.forEach { (key, value) ->
                if (!BLACKLISTED_KEYS.contains(key)) {
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putFloat(key, value.toFloat())
                        is Int -> putInt(key, value)
                        else -> Log.w(TAG, "Unsupported type for key: $key, value: $value")
                    }
                }
            }
            apply()
        }
    }
}