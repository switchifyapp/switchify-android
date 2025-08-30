package com.enaboapps.switchify.backend.preferences

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.enaboapps.switchify.backend.supabase.SupabaseClient
import com.enaboapps.switchify.backend.supabase.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
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
                    Log.d(TAG, "🔐 Auth state changed: ${sessionStatus.javaClass.simpleName}")

                    when (sessionStatus) {
                        is SessionStatus.Authenticated -> {
                            Log.i(
                                TAG,
                                "🟢 User authenticated - userId: ${sessionStatus.session.user?.id}"
                            )
                            Log.i(
                                TAG,
                                "⏳ Pausing SyncQueue and starting smart sync with 3s delay..."
                            )
                            // Pause sync queue to prevent conflicts during initial sync
                            SyncQueue.getInstance().pause()
                            // Wait for Supabase to fully initialize
                            coroutineScope.launch {
                                Log.d(TAG, "⏱️  Waiting 3 seconds for Supabase initialization...")
                                delay(3000)
                                Log.d(TAG, "🚀 Starting smart sync process...")
                                performSmartSync()
                            }
                        }

                        is SessionStatus.NotAuthenticated -> {
                            Log.i(TAG, "🔴 User signed out - clearing sync queue")
                            SyncQueue.getInstance().clearQueue()
                        }

                        SessionStatus.Initializing -> {
                            Log.d(TAG, "🔄 Session initializing...")
                        }

                        is SessionStatus.RefreshFailure -> {
                            Log.w(TAG, "⚠️  Session refresh failure")
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
        if (!checkInitialized()) {
            Log.w(TAG, "Smart sync aborted - PreferenceSync not initialized")
            return
        }

        try {
            Log.i(TAG, "=== SMART SYNC START ===")
            Log.d(TAG, "User authenticated, beginning smart sync process")

            // Pull preferences from Supabase
            Log.d(TAG, "Step 1: Pulling preferences from Supabase...")
            val result = SupabaseManager.getInstance().getUserPreferences()

            result.fold(
                onSuccess = { downloadedPrefs ->
                    Log.d(TAG, "Successfully retrieved preferences from Supabase")
                    Log.d(TAG, "Downloaded preferences count: ${downloadedPrefs.size}")

                    if (downloadedPrefs.isEmpty()) {
                        Log.i(TAG, "Step 2: Empty remote preferences detected - NEW USER")
                        Log.d(TAG, "Proceeding to push local preferences to Supabase")
                        pushLocalPreferences()
                    } else {
                        Log.i(TAG, "Step 2: Remote preferences found - EXISTING USER")
                        Log.d(TAG, "Downloaded preference keys: ${downloadedPrefs.keys}")
                        Log.d(TAG, "Proceeding to apply downloaded preferences locally")
                        applySettings(downloadedPrefs)
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Step 2: Failed to retrieve preferences during smart sync", e)
                    Log.w(TAG, "Error type: ${e.javaClass.simpleName}, message: ${e.message}")
                    // Fallback: still try to push local preferences for new users
                    Log.i(TAG, "Step 3: FALLBACK - Attempting to push local preferences")
                    pushLocalPreferences()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during smart sync", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}, message: ${e.message}")
        } finally {
            // Always resume sync queue after smart sync completes
            Log.i(TAG, "Step 4: Smart sync process completed - resuming sync queue")
            Log.i(TAG, "=== SMART SYNC END ===")
            SyncQueue.getInstance().resume()
        }
    }

    /**
     * Pushes current local preferences to Supabase.
     */
    private suspend fun pushLocalPreferences() {
        try {
            Log.d(TAG, "Reading local preferences...")
            val localPrefs = getAllPreferences()

            if (localPrefs?.isNotEmpty() == true) {
                Log.i(TAG, "Found ${localPrefs.size} local preferences to push")
                Log.d(TAG, "Local preference keys: ${localPrefs.keys}")
                Log.d(TAG, "Uploading local preferences to Supabase...")

                val result = SupabaseManager.getInstance().saveUserPreferences(localPrefs)
                result.fold(
                    onSuccess = {
                        Log.i(TAG, "✅ Local preferences pushed successfully to Supabase")
                        Log.d(TAG, "Successfully uploaded ${localPrefs.size} preferences")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "❌ Failed to push local preferences to Supabase", e)
                        Log.w(TAG, "Error type: ${e.javaClass.simpleName}, message: ${e.message}")
                    }
                )
            } else {
                Log.w(TAG, "No local preferences found to push")
                Log.d(TAG, "Local preferences result: ${localPrefs?.size ?: "null"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error while pushing local preferences", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}, message: ${e.message}")
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
     * Uploads batched preference changes to Supabase.
     * Used by SyncQueue for efficient batched uploads.
     */
    suspend fun uploadBatchedChanges(changes: Map<String, Any>): Result<Unit> {
        if (!checkInitialized()) {
            return Result.failure(Exception("PreferenceSync not initialized"))
        }

        return try {
            // Filter out blacklisted keys
            val filteredChanges =
                changes.filterKeys { !PreferenceManager.Keys.BLACKLISTED_KEYS.contains(it) }

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
     * Gets all non-blacklisted preferences with supported types.
     */
    private fun getAllPreferences(): Map<String, Any>? {
        val prefs = sharedPreferences ?: return null
        return prefs.all.mapNotNull { (key, value) ->
            if (!PreferenceManager.Keys.BLACKLISTED_KEYS.contains(key) && value != null) {
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
                if (!PreferenceManager.Keys.BLACKLISTED_KEYS.contains(key)) {
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