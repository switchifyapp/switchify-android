package com.enaboapps.switchify.switches

import android.util.Log
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.data.FirestoreManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles all Firestore operations for switch events.
 * This includes pushing, pulling, and managing remote switch events.
 */
class SwitchEventFirestore {
    private val tag = "SwitchEventFirestore"
    private val gson = Gson()

    companion object {
        private const val COLLECTION_USER_SWITCHES = "user-switches"
        private const val SWITCHES_COLLECTION = "switches"
    }

    /**
     * Pulls switch events from Firestore.
     *
     * @return Set of switch events from Firestore or null if operation fails
     */
    suspend fun pullEvents(): Set<SwitchEvent>? {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val documents = FirestoreManager.getInstance().queryDocuments(
                    collectionPath = "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION"
                )

                documents.mapNotNull { data ->
                    try {
                        gson.fromJson(gson.toJson(data), SwitchEvent::class.java)
                    } catch (e: Exception) {
                        Log.e(tag, "Error parsing remote switch event: ${e.message}")
                        null
                    }
                }.toSet()
            } catch (e: Exception) {
                Log.e(tag, "Error pulling from Firestore: ${e.message}")
                null
            }
        }
    }

    /**
     * Pushes a switch event to Firestore.
     *
     * @param event The switch event to push
     * @return Boolean indicating success or failure
     */
    suspend fun pushEvent(event: SwitchEvent): Boolean {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return false
        }

        return try {
            val path = "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION/${event.code}"
            FirestoreManager.getInstance().saveDocument(
                path = path,
                data = event.toMap()
            )
            true
        } catch (e: Exception) {
            Log.e(tag, "Error pushing event to Firestore: ${e.message}")
            false
        }
    }

    /**
     * Fetches a single switch event from Firestore.
     *
     * @param code The code of the switch to fetch
     * @return The switch event or null if not found or error occurs
     */
    suspend fun fetchEvent(code: String): SwitchEvent? {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val document = FirestoreManager.getInstance().getDocument(
                    path = "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION/$code"
                ) ?: return@withContext null

                gson.fromJson(gson.toJson(document), SwitchEvent::class.java)
            } catch (e: Exception) {
                Log.e(tag, "Error fetching event from Firestore: ${e.message}")
                null
            }
        }
    }

    /**
     * Removes a switch event from Firestore.
     *
     * @param code The code of the switch to remove
     * @return Boolean indicating success or failure
     */
    suspend fun removeEvent(code: String): Boolean {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return false
        }

        return try {
            val path = "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION/$code"
            FirestoreManager.getInstance().deleteDocument(path)
            true
        } catch (e: Exception) {
            Log.e(tag, "Error removing event from Firestore: ${e.message}")
            false
        }
    }
} 