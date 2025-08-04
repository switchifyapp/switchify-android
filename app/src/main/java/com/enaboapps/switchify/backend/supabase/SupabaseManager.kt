package com.enaboapps.switchify.backend.supabase

import android.util.Log
import com.enaboapps.switchify.backend.supabase.models.TypedUserPreferences
import com.enaboapps.switchify.backend.supabase.models.PreferenceTypeConverter
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * SupabaseManager provides a simplified interface for interacting with Supabase PostgreSQL.
 */
class SupabaseManager {
    
    companion object {
        private const val TAG = "SupabaseManager"
        
        @Volatile
        private var instance: SupabaseManager? = null
        
        fun getInstance(): SupabaseManager {
            return instance ?: synchronized(this) {
                instance ?: SupabaseManager().also { instance = it }
            }
        }
    }
    
    private val supabase = SupabaseClient.client
    private val auth = com.enaboapps.switchify.auth.repository.AuthRepository.instance

    /**
     * Saves user preferences to the database with type information.
     */
    suspend fun saveUserPreferences(
        preferences: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!auth.isUserSignedIn()) {
            return@withContext Result.failure(Exception("User not signed in"))
        }
        
        try {
            val userId = auth.getUserId() ?: return@withContext Result.failure(Exception("No user ID"))
            
            val typedPreferences = PreferenceTypeConverter.toTypedPreferences(preferences)
            val userPrefs = TypedUserPreferences(
                user_id = userId,
                preferences = typedPreferences
            )
            
            // Upsert (insert or update) the preferences with conflict resolution on user_id
            supabase.from("user_preferences").upsert(userPrefs) {
                onConflict = "user_id"
            }
            
            Log.i(TAG, "Typed user preferences saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving typed user preferences", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves user preferences from the database with proper types.
     */
    suspend fun getUserPreferences(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        if (!auth.isUserSignedIn()) {
            return@withContext Result.failure(Exception("User not signed in"))
        }
        
        try {
            val userId = auth.getUserId() ?: return@withContext Result.failure(Exception("No user ID"))
            
            val response = supabase.from("user_preferences")
                .select() {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeSingleOrNull<TypedUserPreferences>()
            
            val preferences = if (response?.preferences != null) {
                PreferenceTypeConverter.fromTypedPreferences(response.preferences)
            } else {
                emptyMap()
            }
            
            Log.i(TAG, "Typed user preferences retrieved successfully")
            Result.success(preferences)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving typed user preferences", e)
            Result.failure(e)
        }
    }

    /**
     * Updates specific preference fields with proper types.
     */
    suspend fun updateUserPreferences(
        updates: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!auth.isUserSignedIn()) {
            return@withContext Result.failure(Exception("User not signed in"))
        }
        
        try {
            val userId = auth.getUserId() ?: return@withContext Result.failure(Exception("No user ID"))
            
            // First get existing preferences
            val existingResult = getUserPreferences()
            val existingPrefs = existingResult.getOrNull() ?: emptyMap()
            
            // Merge with updates
            val mergedPrefs = existingPrefs + updates
            
            // Save the merged preferences
            return@withContext saveUserPreferences(mergedPrefs)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user preferences", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes all user preferences.
     */
    suspend fun deleteUserPreferences(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!auth.isUserSignedIn()) {
            return@withContext Result.failure(Exception("User not signed in"))
        }
        
        try {
            val userId = auth.getUserId() ?: return@withContext Result.failure(Exception("No user ID"))
            
            supabase.from("user_preferences")
                .delete {
                    filter {
                        eq("user_id", userId)
                    }
                }
            
            Log.i(TAG, "User preferences deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user preferences", e)
            Result.failure(e)
        }
    }

    /**
     * Sets up a real-time listener for changes to user preferences.
     * Note: Real-time subscriptions need proper setup in Supabase project
     */
    fun listenToUserPreferences(): Flow<PostgresAction>? {
        return if (auth.isUserSignedIn()) {
            val userId = auth.getUserId()
            if (userId != null) {
                try {
                    supabase.realtime.channel("user_preferences").postgresChangeFlow<PostgresAction>(
                        schema = "public"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up real-time listener", e)
                    null
                }
            } else null
        } else null
    }
}