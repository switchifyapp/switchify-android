package com.enaboapps.switchify.service.gestures.patterns.store

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Class responsible for managing gesture patterns, including saving to and loading from a file.
 *
 * @property context The application context.
 */
class GesturePatternStore(context: Context) {
    private val dao = GesturePatternDatabase.getDatabase(context).gesturePatternDao()

    companion object {
        private const val TAG = "GesturePatternStore"
    }

    suspend fun isEmpty(): Boolean = dao.getAllPatterns().first().isEmpty()

    /**
     * Adds a new pattern to the database.
     *
     * @param name The name of the pattern.
     * @param gestures The list of gestures in the pattern.
     * @return The ID of the newly added pattern.
     */
    suspend fun addPattern(name: String, gestures: List<GestureData>): String {
        return try {
            val newPattern = GesturePattern(gestures = gestures, name = name)
            withContext(Dispatchers.IO) {
                dao.insertPattern(GesturePatternEntity.fromGesturePattern(newPattern))
                dao.insertGestures(gestures.map {
                    GestureDataEntity.fromGestureData(newPattern.id, it)
                })
            }
            newPattern.id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding pattern: ${e.message}")
            ""
        }
    }

    /**
     * Removes a pattern from the database.
     *
     * @param id The ID of the pattern to be removed.
     */
    suspend fun removePattern(id: String) {
        try {
            withContext(Dispatchers.IO) {
                dao.deleteGesturesForPattern(id)
                dao.deletePatternById(id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing pattern: ${e.message}")
        }
    }

    /**
     * Updates an existing pattern in the database.
     *
     * @param id The ID of the pattern to be updated.
     * @param name The new name for the pattern.
     * @param gestures The new list of gestures for the pattern.
     */
    suspend fun updatePattern(id: String, name: String, gestures: List<GestureData>) {
        try {
            val updatedPattern = GesturePattern(id = id, gestures = gestures, name = name)
            withContext(Dispatchers.IO) {
                dao.insertPattern(GesturePatternEntity.fromGesturePattern(updatedPattern))
                dao.deleteGesturesForPattern(id)
                dao.insertGestures(gestures.map {
                    GestureDataEntity.fromGestureData(id, it)
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pattern: ${e.message}")
        }
    }
    
    /**
     * Updates just the name of an existing pattern in the database.
     *
     * @param id The ID of the pattern to be updated.
     * @param name The new name for the pattern.
     */
    suspend fun updatePatternName(id: String, name: String) {
        try {
            // Get the existing pattern with its gestures
            val existingPattern = withContext(Dispatchers.IO) {
                dao.getPatternById(id)
            } ?: return
            
            // Extract the gestures from the existing pattern
            val gestures = existingPattern.gestures.map { it.toGestureData() }
            
            // Use the updatePattern method to update the name while preserving the gestures
            updatePattern(id, name, gestures)
            
            Log.d(TAG, "Updated pattern name for ID: $id to: $name")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pattern name: ${e.message}")
        }
    }

    /**
     * Retrieves all patterns from local storage.
     *
     * @return The list of all patterns.
     */
    suspend fun getPatterns(): List<GesturePattern> {
        return try {
            dao.getAllPatterns().first().map { it.toGesturePattern() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting patterns: ${e.message}")
            emptyList()
        }
    }

    /**
     * Retrieves a pattern by its ID from local storage.
     *
     * @param id The ID of the pattern to be retrieved.
     * @return The GesturePattern object or null if not found.
     */
    suspend fun getPattern(id: String): GesturePattern? {
        return try {
            dao.getPatternById(id)?.toGesturePattern()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pattern: ${e.message}")
            null
        }
    }
}