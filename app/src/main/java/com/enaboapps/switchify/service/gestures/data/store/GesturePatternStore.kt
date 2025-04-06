package com.enaboapps.switchify.service.gestures.data.store

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Class responsible for managing gesture patterns, including saving to and loading from a file.
 *
 * @property context The application context.
 */
class GesturePatternStore(private val context: Context) {
    private val fileName = "gesture_patterns.json"
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var file = File(context.filesDir, fileName)
    private var items: MutableList<GesturePattern> = mutableListOf()

    companion object {
        private const val TAG = "GesturePatternStore"
    }

    init {
        try {
            if (!file.exists()) {
                file.createNewFile()
                file.writeText("[]")
            } else {
                loadItems()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing file: ${e.message}")
        }
    }

    /**
     * Loads patterns from the local file and synchronizes with Firestore.
     */
    private fun loadItems() {
        try {
            val json = file.readText()
            val type = object : TypeToken<List<GesturePattern>>() {}.type
            items =
                gson.fromJson<List<GesturePattern>>(json, type)?.toMutableList() ?: mutableListOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading patterns: ${e.message}")
            items = mutableListOf()
        }
    }

    /**
     * Checks if there are no patterns stored.
     *
     * @return True if there are no patterns, false otherwise.
     */
    fun isEmpty(): Boolean = items.isEmpty()

    /**
     * Adds a new pattern to the store and saves it to both local storage and Firestore.
     *
     * @param name The name of the pattern.
     * @param gestures The list of gestures in the pattern.
     * @return The ID of the newly added pattern.
     */
    fun addPattern(name: String, gestures: List<GestureData>): String {
        try {
            val newPattern = GesturePattern(gestures = gestures, name = name)
            items.add(newPattern)

            coroutineScope.launch {
                savePatterns()
            }

            return newPattern.id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding pattern: ${e.message}")
            return ""
        }
    }

    /**
     * Removes a pattern from both local storage and Firestore.
     *
     * @param id The ID of the pattern to be removed.
     */
    fun removePattern(id: String) {
        try {
            items.removeIf { it.id == id }

            coroutineScope.launch {
                savePatterns()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing pattern: ${e.message}")
        }
    }

    /**
     * Updates an existing pattern in both local storage and Firestore.
     *
     * @param id The ID of the pattern to be updated.
     * @param name The new name for the pattern.
     * @param gestures The new list of gestures for the pattern.
     */
    fun updatePattern(id: String, name: String, gestures: List<GestureData>) {
        try {
            val index = items.indexOfFirst { it.id == id }
            if (index != -1) {
                val updatedPattern = GesturePattern(id = id, gestures = gestures, name = name)
                items[index] = updatedPattern

                coroutineScope.launch {
                    savePatterns()
                }
            } else {
                Log.w(TAG, "Pattern not found for update: $id")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pattern: ${e.message}")
        }
    }

    /**
     * Retrieves all patterns from local storage.
     *
     * @return The list of all patterns.
     */
    fun getPatterns(): List<GesturePattern> = items.toList()

    /**
     * Saves the current list of patterns to the local file.
     */
    private fun savePatterns() {
        try {
            file.writeText(gson.toJson(items))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving patterns: ${e.message}")
        }
    }

    /**
     * Retrieves a pattern by its ID from local storage.
     *
     * @param id The ID of the pattern to be retrieved.
     * @return The GesturePattern object or null if not found.
     */
    fun getPattern(id: String): GesturePattern? {
        return try {
            items.find { it.id == id }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pattern: ${e.message}")
            null
        }
    }
}