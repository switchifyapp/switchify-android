package com.enaboapps.switchify.service.actions.custom.store

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.data.FirestoreManager
import com.enaboapps.switchify.service.actions.custom.store.data.ACTIONS
import com.enaboapps.switchify.service.actions.custom.store.data.ActionExtra
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Data class representing an Action.
 *
 * @property id The unique identifier for the action.
 * @property action The action to be performed.
 * @property text The text associated with the action.
 * @property extra Additional data for the action.
 */
data class Action(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("action") val action: String,
    @SerializedName("text") val text: String,
    @SerializedName("extra") val extra: ActionExtra? = null
) {
    companion object {
        /**
         * Parses a JSON string to create an Action object.
         *
         * @param json The JSON string representing the action.
         * @return The Action object or null if parsing fails.
         */
        fun fromJson(json: String): Action? = try {
            Gson().fromJson(json, Action::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e("Action", "Error parsing JSON: ${e.message}")
            null
        }
    }

    /**
     * Converts the Action object to a JSON string.
     *
     * @return The JSON string representation of the Action object.
     */
    fun toJson(): String = Gson().toJson(this)

    /**
     * Converts the Action object to a Map suitable for Firestore storage.
     *
     * @return The Map representation of the Action object.
     */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "action" to action,
            "text" to text
        )
        extra?.let { map["extra"] = it }
        return map
    }
}

/**
 * Class responsible for managing actions, including saving to and loading from a file,
 * and synchronizing with Firestore.
 *
 * The class maintains both a local cache in a JSON file and a remote copy in Firestore,
 * providing methods to synchronize between them.
 *
 * @property context The application context.
 */
class ActionStore(private val context: Context) {
    private val gson = Gson()
    private val fileName = "actions.json"
    private val file = File(context.filesDir, fileName)
    private val tag = "ActionStore"
    private val firestoreManager = FirestoreManager.getInstance()
    private val authManager = AuthManager.instance
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var items: MutableList<Action> = mutableListOf()

    companion object {
        private const val COLLECTION_USER_ACTIONS = "user-actions"
        private const val ACTIONS_COLLECTION = "actions"
    }

    init {
        try {
            if (!file.exists()) {
                file.createNewFile()
                file.writeText("[]")
            }
            loadItems()
        } catch (e: IOException) {
            Log.e(tag, "Error initializing file: ${e.message}")
        }
    }

    /**
     * Constructs the Firestore path for a given action ID.
     *
     * @param actionId The ID of the action.
     * @return The Firestore path for the action.
     */
    private fun getActionPath(actionId: String): String {
        val userId = authManager.getUserId() ?: Log.d(tag, "Could not get user ID")
        return "$COLLECTION_USER_ACTIONS/$userId/$ACTIONS_COLLECTION/$actionId"
    }

    /**
     * Loads actions from the local file and synchronizes with Firestore.
     */
    private fun loadItems() {
        try {
            val json = file.readText()
            val type = object : TypeToken<List<Action>>() {}.type
            items = gson.fromJson<List<Action>>(json, type)?.toMutableList() ?: mutableListOf()
        } catch (e: Exception) {
            Log.e(tag, "Error loading actions: ${e.message}")
            items = mutableListOf()
        }
    }

    /**
     * Pulls all actions from Firestore and updates local storage.
     * This will overwrite any local changes with the data from Firestore.
     */
    fun pullActionsFromFirestore() {
        val userId = authManager.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return
        }

        coroutineScope.launch {
            try {
                // Query the collection with a prefix match on the user ID
                val documents = firestoreManager.queryDocuments(
                    collectionPath = "$COLLECTION_USER_ACTIONS/$userId/$ACTIONS_COLLECTION"
                )
                // Map the retrieved documents to Action objects
                val remoteActions = documents.mapNotNull { data ->
                    try {
                        Action(
                            id = data["id"] as? String ?: return@mapNotNull null,
                            action = data["action"] as? String ?: return@mapNotNull null,
                            text = data["text"] as? String ?: return@mapNotNull null,
                            extra = ActionExtra.fromFirestore(data)
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "Error parsing remote action: ${e.message}")
                        null
                    }
                }

                if (remoteActions.isNotEmpty()) {
                    items = remoteActions.toMutableList()
                    saveActions()
                    Log.i(tag, "Successfully pulled ${remoteActions.size} actions from Firestore")
                } else {
                    Log.i(tag, "No actions found in Firestore")

                    if (items.isNotEmpty()) {
                        pushActionsToFirestore() // Push local changes to Firestore
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error pulling from Firestore: ${e.message}")
            }
        }
    }

    /**
     * Pushes all local actions to Firestore.
     * This will overwrite any remote changes with the local data.
     */
    fun pushActionsToFirestore() {
        try {
            coroutineScope.launch {
                items.forEach { action ->
                    firestoreManager.saveDocument(
                        path = getActionPath(action.id),
                        data = action.toMap()
                    )
                    Log.i(tag, "Successfully pushed action ${action.id} to Firestore")
                }
            }
            Log.i(tag, "Successfully pushed ${items.size} actions to Firestore")
        } catch (e: Exception) {
            Log.e(tag, "Error pushing to Firestore: ${e.message}")
        }
    }

    /**
     * Checks if there are no actions stored.
     *
     * @return True if there are no actions, false otherwise.
     */
    fun isEmpty(): Boolean = items.isEmpty()

    /**
     * Retrieves the list of available actions.
     *
     * @return The list of available actions.
     */
    fun getAvailableActions(): List<String> = ACTIONS

    /**
     * Adds a new action to the store and saves it to both local storage and Firestore.
     *
     * @param action The action to be performed.
     * @param text The text associated with the action.
     * @param extra Additional data for the action.
     * @return The ID of the newly added action.
     */
    fun addAction(action: String, text: String, extra: ActionExtra? = null): String {
        try {
            val newAction = Action(action = action, text = text, extra = extra)
            items.add(newAction)
            saveActions()

            coroutineScope.launch {
                firestoreManager.saveDocument(
                    path = getActionPath(newAction.id),
                    data = newAction.toMap()
                )
            }

            return newAction.id
        } catch (e: Exception) {
            Log.e(tag, "Error adding action: ${e.message}")
            return ""
        }
    }

    /**
     * Removes an action from both local storage and Firestore.
     *
     * @param id The ID of the action to be removed.
     */
    fun removeAction(id: String) {
        try {
            items.removeIf { it.id == id }
            saveActions()

            coroutineScope.launch {
                firestoreManager.deleteDocument(path = getActionPath(id))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error removing action: ${e.message}")
        }
    }

    /**
     * Updates an existing action in both local storage and Firestore.
     *
     * @param id The ID of the action to be updated.
     * @param action The new action to be performed (optional).
     * @param text The new text associated with the action (optional).
     * @param extra The new additional data for the action (optional).
     */
    fun updateAction(
        id: String,
        action: String? = null,
        text: String? = null,
        extra: ActionExtra? = null
    ) {
        try {
            val index = items.indexOfFirst { it.id == id }
            if (index != -1) {
                val currentItem = items[index]
                val updatedItem = currentItem.copy(
                    action = action ?: currentItem.action,
                    text = text ?: currentItem.text,
                    extra = extra ?: currentItem.extra
                )
                items[index] = updatedItem
                saveActions()

                coroutineScope.launch {
                    firestoreManager.saveDocument(
                        path = getActionPath(id),
                        data = updatedItem.toMap()
                    )
                }
            } else {
                Log.w(tag, "Action not found for update: $id")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error updating action: ${e.message}")
        }
    }

    /**
     * Retrieves all actions from local storage.
     *
     * @return The list of all actions.
     */
    fun getActions(): List<Action> = items.toList()

    /**
     * Saves the current list of actions to the local file.
     */
    private fun saveActions() {
        try {
            val json = gson.toJson(items)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(tag, "Error saving actions: ${e.message}")
        }
    }

    /**
     * Retrieves an action by its ID from local storage.
     *
     * @param id The ID of the action to be retrieved.
     * @return The Action object or null if not found.
     */
    fun getAction(id: String): Action? {
        return try {
            items.find { it.id == id }
        } catch (e: Exception) {
            Log.e(tag, "Error getting action: ${e.message}")
            null
        }
    }
}