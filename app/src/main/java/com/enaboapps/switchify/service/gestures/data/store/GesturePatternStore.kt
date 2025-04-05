package com.enaboapps.switchify.service.gestures.data.store

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.data.FileManager
import com.enaboapps.switchify.backend.data.FirestoreManager
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Class responsible for managing gesture patterns, including saving to and loading from a file,
 * and synchronizing with Firestore.
 *
 * The class maintains both a local cache in a JSON file and a remote copy in Firestore,
 * providing methods to synchronize between them.
 *
 * @property context The application context.
 */
class GesturePatternStore(private val context: Context) {
    private val gson = Gson()
    private val fileName = "gesture_patterns.json"
    private val tag = "GesturePatternStore"
    private val firestoreManager = FirestoreManager.getInstance()
    private val authManager = AuthManager.instance
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val fileManager = FileManager.create(context)

    private var items: MutableList<GesturePattern> = mutableListOf()

    companion object {
        private const val COLLECTION_USER_GESTURE_PATTERNS = "user-gesture-patterns"
        private const val PATTERNS_COLLECTION = "patterns"
    }

    init {
        try {
            coroutineScope.launch {
                fileManager.createFile(fileName)
                loadItems()
            }
        } catch (e: IOException) {
            Log.e(tag, "Error initializing file: ${e.message}")
        }
    }

    /**
     * Constructs the Firestore path for a given pattern ID.
     *
     * @param patternId The ID of the pattern.
     * @return The Firestore path for the pattern.
     */
    private fun getPatternPath(patternId: String): String {
        val userId = authManager.getUserId() ?: Log.d(tag, "Could not get user ID")
        return "$COLLECTION_USER_GESTURE_PATTERNS/$userId/$PATTERNS_COLLECTION/$patternId"
    }

    /**
     * Loads patterns from the local file and synchronizes with Firestore.
     */
    private suspend fun loadItems() {
        try {
            val type = object : TypeToken<List<GesturePattern>>() {}.type
            val result = fileManager.readJson<List<GesturePattern>>(fileName, type)
            if (result.isSuccess) {
                items = (result.getOrNull() ?: emptyList()).toMutableList()
            } else {
                Log.e(tag, "Error loading patterns: ${result.exceptionOrNull()?.message}")
                items = mutableListOf()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error loading patterns: ${e.message}")
            items = mutableListOf()
        }
    }

    /**
     * Pulls all patterns from Firestore and updates local storage.
     * This will overwrite any local changes with the data from Firestore.
     */
    fun pullPatternsFromFirestore() {
        val userId = authManager.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return
        }

        coroutineScope.launch {
            try {
                val documents = firestoreManager.queryDocuments(
                    collectionPath = "$COLLECTION_USER_GESTURE_PATTERNS/$userId/$PATTERNS_COLLECTION"
                )
                val remotePatterns = documents.mapNotNull { data ->
                    try {
                        GesturePattern(
                            id = data["id"] as? String ?: return@mapNotNull null,
                            gestures = data["gestures"] as? List<GestureData>
                                ?: return@mapNotNull null,
                            name = data["name"] as? String ?: return@mapNotNull null
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "Error parsing remote pattern: ${e.message}")
                        null
                    }
                }

                if (remotePatterns.isNotEmpty()) {
                    items = remotePatterns.toMutableList()
                    savePatterns()
                    Log.i(tag, "Successfully pulled ${remotePatterns.size} patterns from Firestore")
                } else {
                    Log.i(tag, "No patterns found in Firestore")

                    if (items.isNotEmpty()) {
                        pushPatternsToFirestore()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error pulling from Firestore: ${e.message}")
            }
        }
    }

    /**
     * Pushes all local patterns to Firestore.
     * This will overwrite any remote changes with the local data.
     */
    fun pushPatternsToFirestore() {
        try {
            coroutineScope.launch {
                items.forEach { pattern ->
                    firestoreManager.saveDocument(
                        path = getPatternPath(pattern.id),
                        data = pattern.toMap()
                    )
                    Log.i(tag, "Successfully pushed pattern ${pattern.id} to Firestore")
                }
            }
            Log.i(tag, "Successfully pushed ${items.size} patterns to Firestore")
        } catch (e: Exception) {
            Log.e(tag, "Error pushing to Firestore: ${e.message}")
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
                firestoreManager.saveDocument(
                    path = getPatternPath(newPattern.id),
                    data = newPattern.toMap()
                )
            }

            return newPattern.id
        } catch (e: Exception) {
            Log.e(tag, "Error adding pattern: ${e.message}")
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
                firestoreManager.deleteDocument(path = getPatternPath(id))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error removing pattern: ${e.message}")
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
                    firestoreManager.saveDocument(
                        path = getPatternPath(id),
                        data = updatedPattern.toMap()
                    )
                }
            } else {
                Log.w(tag, "Pattern not found for update: $id")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error updating pattern: ${e.message}")
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
    private suspend fun savePatterns() {
        try {
            fileManager.writeJson(fileName, items)
        } catch (e: Exception) {
            Log.e(tag, "Error saving patterns: ${e.message}")
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
            Log.e(tag, "Error getting pattern: ${e.message}")
            null
        }
    }
}