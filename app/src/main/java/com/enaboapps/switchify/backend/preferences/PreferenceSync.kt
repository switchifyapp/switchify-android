package com.enaboapps.switchify.backend.preferences

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.enaboapps.switchify.backend.supabase.SupabaseManager
import com.enaboapps.switchify.backend.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages synchronization between local SharedPreferences and Supabase PostgreSQL.
 * Handles automatic syncing of user preferences across devices with support for
 * real-time updates and type-safe storage.
 */
class PreferenceSync private constructor() {
    private var sharedPreferences: SharedPreferences? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false
    private var authObserverStarted = false

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
        startAuthObserver()
    }

    /**
     * Starts observing authentication state changes for automatic preference sync.
     */
    private fun startAuthObserver() {
        if (authObserverStarted) return
        
        authObserverStarted = true
        Log.d(TAG, "Starting authentication state observer")
        
        coroutineScope.launch {
            try {
                SupabaseClient.client.auth.sessionStatus.collect { sessionStatus ->
                    when (sessionStatus) {
                        is SessionStatus.Authenticated -> {
                            Log.i(TAG, "User signed in - performing smart sync after delay")
                            // Wait for Supabase to fully initialize
                            coroutineScope.launch {
                                delay(3000)
                                performSmartSync()
                            }
                        }
                        is SessionStatus.NotAuthenticated -> {
                            Log.i(TAG, "User signed out - clearing sync queue")
                            SyncQueue.getInstance().clearQueue()
                        }
                        SessionStatus.Initializing -> {
                            Log.d(TAG, "Initializing session")
                        }
                        is SessionStatus.RefreshFailure -> {
                            Log.w(TAG, "Session refresh failure")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in auth state observer", e)
            }
        }
    }

    /**
     * Performs smart sync: pull from Supabase, if empty assume new user and push local prefs.
     */
    private suspend fun performSmartSync() {
        if (!checkInitialized()) return
        
        try {
            Log.i(TAG, "Starting smart sync")
            
            // Pull preferences from Supabase
            val result = SupabaseManager.getInstance().getUserPreferences()
            
            result.fold(
                onSuccess = { downloadedPrefs ->
                    if (downloadedPrefs.isEmpty()) {
                        Log.i(TAG, "No remote preferences found - new user, pushing local preferences")
                        pushLocalPreferences()
                    } else {
                        Log.i(TAG, "Remote preferences found - existing user, applying downloaded preferences")
                        applySettings(downloadedPrefs)
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to retrieve preferences during smart sync", e)
                    // Fallback: still try to push local preferences for new users
                    Log.i(TAG, "Fallback: pushing local preferences")
                    pushLocalPreferences()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during smart sync", e)
        }
    }

    /**
     * Pushes current local preferences to Supabase.
     */
    private suspend fun pushLocalPreferences() {
        try {
            val localPrefs = getAllPreferences()
            if (localPrefs?.isNotEmpty() == true) {
                val result = SupabaseManager.getInstance().saveUserPreferences(localPrefs)
                result.fold(
                    onSuccess = {
                        Log.i(TAG, "Local preferences pushed successfully")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to push local preferences", e)
                    }
                )
            } else {
                Log.d(TAG, "No local preferences to push")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing local preferences", e)
        }
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
     * Uploads batched preference changes to Supabase.
     * Used by SyncQueue for efficient batched uploads.
     */
    suspend fun uploadBatchedChanges(changes: Map<String, Any>): Result<Unit> {
        if (!checkInitialized()) {
            return Result.failure(Exception("PreferenceSync not initialized"))
        }

        return try {
            // Filter out blacklisted keys
            val filteredChanges = changes.filterKeys { !BLACKLISTED_KEYS.contains(it) }
            
            if (filteredChanges.isEmpty()) {
                Log.d(TAG, "No valid changes to upload after filtering")
                return Result.success(Unit)
            }

            // Get current preferences and merge with changes
            val currentPrefs = getAllPreferences() ?: emptyMap()
            val mergedPrefs = currentPrefs + filteredChanges
            
            val result = SupabaseManager.getInstance().saveUserPreferences(mergedPrefs)
            result.fold(
                onSuccess = {
                    Log.i(TAG, "Batched changes uploaded successfully: ${filteredChanges.keys}")
                    Result.success(Unit)
                },
                onFailure = { e ->
                    Log.e(TAG, "Error uploading batched changes", e)
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading batched changes", e)
            Result.failure(e)
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