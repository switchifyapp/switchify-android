package com.enaboapps.switchify.switches

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.data.FirestoreManager
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.scanning.ScanMode
import com.enaboapps.switchify.utils.Logger
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SwitchEventStore manages the storage and synchronization of switch events between local storage
 * and Firestore. It provides functionality for local storage, cloud synchronization, and remote
 * switch management.
 *
 * Features:
 * - Local storage using JSON file
 * - Cloud synchronization with Firestore
 * - Remote switch discovery and import
 * - CRUD operations for switches
 * - Switch configuration validation
 *
 * @property context The application context used for file operations and preferences
 * @property localOnly Whether to only use local storage (no cloud synchronization)
 */
class SwitchEventStore(private val context: Context, private val localOnly: Boolean = false) {
    // Core data storage
    private val switchEvents = mutableSetOf<SwitchEvent>()
    private val fileName = "switch_events.json"
    private val file: File
        get() = File(context.applicationContext.filesDir, fileName)

    // Utilities and managers
    private val gson = Gson()
    private val tag = "SwitchEventStore"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val EVENTS_UPDATED = "com.enaboapps.switchify.EVENTS_UPDATED"
        private const val COLLECTION_USER_SWITCHES = "user-switches"
        private const val SWITCHES_COLLECTION = "switches"
    }

    init {
        readFile()
    }

    /**
     * Represents a remote switch that can be imported to the device.
     *
     * @property type The type of the switch
     * @property name The name of the switch
     * @property code The unique identifier code for the switch
     * @property isOnDevice Indicates if the switch is already present on the device
     */
    data class RemoteSwitchInfo(
        val type: String,
        val name: String,
        val code: String,
        val isOnDevice: Boolean
    )

    /**
     * Fetches all available switches from Firestore, including information about
     * which ones are already on the device.
     *
     * @return Result containing list of available remote switches or error
     */
    suspend fun fetchAvailableSwitches(): Result<List<RemoteSwitchInfo>> {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return Result.failure(Exception("User ID not available"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val documents = FirestoreManager.getInstance().queryDocuments(
                    collectionPath = "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION"
                )

                val switches = documents.mapNotNull { data ->
                    try {
                        val event = gson.fromJson(gson.toJson(data), SwitchEvent::class.java)
                        RemoteSwitchInfo(
                            type = event.type,
                            name = event.name,
                            code = event.code,
                            isOnDevice = switchEvents.any { it.code == event.code }
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "Error parsing remote switch: ${e.message}")
                        null
                    }
                }

                Log.d(tag, "Returning ${switches.size} remote switches")
                Result.success(switches)
            } catch (e: Exception) {
                Log.e(tag, "Error fetching remote switches", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Imports a single switch from Firestore based on its code.
     * Only imports if the switch isn't already on the device.
     *
     * @param code The code of the switch to import
     * @return Result containing the imported SwitchEvent if successful
     */
    suspend fun importSwitch(code: String): Result<SwitchEvent> {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return Result.failure(Exception("User ID not available"))
        }

        if (switchEvents.any { it.code == code }) {
            Log.d(tag, "Switch with code $code is already on device")
            return Result.failure(Exception("Switch is already on device"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val document = FirestoreManager.getInstance().getDocument(
                    path = "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION/$code"
                )

                val switchEvent = gson.fromJson(gson.toJson(document), SwitchEvent::class.java)
                switchEvents.add(switchEvent)
                saveToFile()

                Log.d(tag, "Successfully imported switch: ${switchEvent.name}")
                Logger.logEvent("Imported switch: $code")
                Result.success(switchEvent)
            } catch (e: Exception) {
                Logger.logEvent("Error importing switch: $code, ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Constructs the Firestore path for a given switch event code.
     */
    private fun getSwitchPath(code: String): String {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.d(tag, "Could not get user ID")
            return ""
        }
        return "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION/$code"
    }

    /**
     * Pulls all switch events from Firestore and updates local storage.
     */
    private fun pullEventsFromFirestore() {
        if (localOnly) {
            return
        }
        
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return
        }

        coroutineScope.launch {
            try {
                val documents = FirestoreManager.getInstance().queryDocuments(
                    collectionPath = "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION"
                )

                val remoteEvents = documents.mapNotNull { data ->
                    try {
                        gson.fromJson(gson.toJson(data), SwitchEvent::class.java)
                    } catch (e: Exception) {
                        Log.e(tag, "Error parsing remote switch event: ${e.message}")
                        null
                    }
                }.toSet()

                if (remoteEvents.isNotEmpty()) {
                    switchEvents.clear()
                    switchEvents.addAll(remoteEvents)
                    saveToFile()
                    Log.i(
                        tag,
                        "Successfully pulled ${remoteEvents.size} switch events from Firestore"
                    )
                } else {
                    Log.i(tag, "No switch events found in Firestore")
                    if (switchEvents.isNotEmpty()) {
                        pushEventsToFirestore()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error pulling from Firestore: ${e.message}")
            }
        }
    }

    /**
     * Pushes all local switch events to Firestore.
     */
    private fun pushEventsToFirestore() {
        if (localOnly) {
            return
        }
        coroutineScope.launch {
            try {
                switchEvents.forEach { event ->
                    val path = getSwitchPath(event.code)
                    if (path.isNotEmpty()) {
                        FirestoreManager.getInstance().saveDocument(
                            path = path,
                            data = event.toMap()
                        )
                    }
                }
                Log.i(tag, "Successfully pushed ${switchEvents.size} switch events to Firestore")
                Logger.logEvent("Pushed ${switchEvents.size} switch events to Firestore")
            } catch (e: Exception) {
                Logger.logEvent("Error pushing to Firestore: ${e.message}")
            }
        }
    }

    /**
     * Adds a new switch event to both local storage and Firestore.
     *
     * @param switchEvent The switch event to add
     * @param completion The completion callback to be called after the add is complete
     */
    fun add(switchEvent: SwitchEvent, completion: ((Boolean) -> Unit)) {
        if (switchEvents.add(switchEvent)) {
            try {
                saveToFile()
                completion(true)

                coroutineScope.launch {
                    val path = getSwitchPath(switchEvent.code)
                    if (path.isNotEmpty()) {
                        FirestoreManager.getInstance().saveDocument(
                            path = path,
                            data = switchEvent.toMap()
                        )
                        Logger.logEvent("Added switch: ${switchEvent.name}")
                    }
                }
            } catch (e: Exception) {
                completion(false)
                Log.e(tag, "Error adding switch event", e)
            }
        } else {
            completion(false)
        }
    }

    /**
     * Updates an existing switch event in both local storage and Firestore.
     *
     * @param switchEvent The switch event to update
     * @param completion The completion callback to be called after the update is complete
     */
    fun update(switchEvent: SwitchEvent, completion: ((Boolean) -> Unit)) {
        var index = -1
        index = switchEvents.indexOfFirst { it.code == switchEvent.code }
        if (index != -1) {
            try {
                switchEvents.forEachIndexed { i, event ->
                    if (i == index) {
                        event.setValuesFromOther(switchEvent)
                    }
                }
                saveToFile()
                completion(true)
                coroutineScope.launch {
                    val path = getSwitchPath(switchEvent.code)
                    if (path.isNotEmpty()) {
                        FirestoreManager.getInstance().saveDocument(
                            path = path,
                            data = switchEvent.toMap()
                        )
                        Logger.logEvent("Updated switch: ${switchEvent.name}")
                    }
                }
            } catch (e: Exception) {
                completion(false)
                Log.e(tag, "Error updating switch event", e)
            }
        } else {
            completion(false)
        }
    }

    /**
     * Removes a switch event from both local storage and Firestore.
     *
     * @param switchEvent The switch event to remove
     * @param handler The handler to be called after the switch event is removed
     */
    fun remove(switchEvent: SwitchEvent, handler: ((Boolean) -> Unit)) {
        if (switchEvents.removeIf { it.code == switchEvent.code }) {
            try {
                saveToFile()
                Logger.logEvent("Removed switch: ${switchEvent.name}")
                handler(true)
            } catch (e: Exception) {
                handler(false)
                Log.e(tag, "Error removing switch event", e)
            }
        } else {
            handler(false)
            Log.e(tag, "Switch event not found")
        }
    }

    /**
     * Removes a remote switch event by its code.
     *
     * @param code The code of the remote switch event to remove
     */
    suspend fun removeRemote(code: String): Result<Unit> {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return Result.failure(Exception("User ID not available"))
        }

        return withContext(Dispatchers.IO) {
            try {
                FirestoreManager.getInstance().deleteDocument(
                    path = "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION/$code"
                )
                switchEvents.removeIf { it.code == code }
                saveToFile()
                Logger.logEvent("Removed remote switch: $code")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "Error removing remote switch $code", e)
                Logger.logEvent("Error removing remote switch: $code, ${e.message}")
                Result.failure(e)
            }
        }
    }


    /**
     * Finds a switch event by its code.
     *
     * @param code The code of the switch event to find
     * @return The found switch event or null if not found
     */
    fun find(code: String): SwitchEvent? =
        switchEvents.find { it.code == code }?.also {
            Log.d(tag, "Found switch event for code $code")
        } ?: run {
            Log.d(tag, "No switch event found for code $code")
            null
        }

    /**
     * Finds an external switch event by its code.
     *
     * @param code The code of the switch event to find
     * @return The found switch event or null if not found
     */
    fun findExternal(code: String): SwitchEvent? =
        switchEvents.find { it.type == SWITCH_EVENT_TYPE_EXTERNAL && it.code == code && it.isOnDevice }
            ?.also {
                Log.d(tag, "Found external switch event for code $code")
            } ?: run {
            Log.d(tag, "No external switch event found for code $code")
            null
        }

    /**
     * Finds a camera switch event by its code.
     *
     * @param code The code of the switch event to find
     * @return The found switch event or null if not found
     */
    fun findCamera(code: String): SwitchEvent? =
        switchEvents.find { it.type == SWITCH_EVENT_TYPE_CAMERA && it.code == code && it.isOnDevice }
            ?.also {
                Log.d(tag, "Found camera switch event for code $code")
            } ?: run {
            Log.d(tag, "No camera switch event found for code $code")
            null
        }

    /**
     * Returns the total number of switch events.
     *
     * @return The count of switch events
     */
    fun getCount(): Int = switchEvents.size

    /**
     * Returns a copy of all switch events.
     *
     * @return An immutable set of all switch events
     */
    fun getSwitchEvents(): Set<SwitchEvent> = switchEvents.toSet()

    /**
     * Validates a switch event's data.
     *
     * @param switchEvent The switch event to validate
     * @return true if the switch event is valid, false otherwise
     */
    fun validateSwitchEvent(switchEvent: SwitchEvent): Boolean {
        val hasName = switchEvent.name.isNotBlank()
        val hasCode = switchEvent.code.isNotBlank()

        if (!validateExtra(switchEvent.pressAction.id, switchEvent.pressAction.extra)) {
            Log.d(tag, "Press action extra is invalid")
            return false
        }

        for (holdAction in switchEvent.holdActions) {
            if (!validateExtra(holdAction.id, holdAction.extra)) {
                Log.d(tag, "Hold action extra is invalid")
                return false
            }
        }

        Log.d(tag, "Switch event validation - hasName: $hasName, hasCode: $hasCode")
        return hasName && hasCode
    }

    /**
     * Validates the extra data for a switch action.
     *
     * @param type The type of the action
     * @param extra The extra data to validate
     * @return true if the extra data is valid for the action type, false otherwise
     */
    private fun validateExtra(type: Int, extra: SwitchActionExtra?): Boolean {
        return when (type) {
            SwitchAction.ACTION_PERFORM_USER_ACTION -> {
                extra != null && extra.myActionsId != null && extra.myActionName != null
            }

            else -> true
        }
    }

    /**
     * Reads switch events from the local file.
     */
    private fun readFile() {
        if (file.exists()) {
            try {
                val gsonBuilder = GsonBuilder()
                gsonBuilder.registerTypeAdapter(SwitchEvent::class.java, SwitchEventTypeAdapter())
                val gson = gsonBuilder.create()
                val type = object : TypeToken<Set<SwitchEvent>>() {}.type
                val events: Set<SwitchEvent> = gson.fromJson(file.readText(), type)
                switchEvents.clear()
                switchEvents.addAll(events)
                switchEvents.forEach { it.isOnDevice = true }
                switchEvents.forEach { it.log() }
            } catch (e: Exception) {
                Log.e(tag, "Error reading from file", e)
                deleteFile()
                Toast.makeText(
                    context,
                    "Error reading from file. Please reconfigure your switches.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Log.d(tag, "File does not exist")
        }
    }

    /**
     * Deletes the local storage file.
     */
    private fun deleteFile() {
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Saves switch events to the local file and broadcasts an update event.
     */
    private fun saveToFile() {
        try {
            file.writeText(gson.toJson(switchEvents))
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(EVENTS_UPDATED))
        } catch (e: Exception) {
            Log.e(tag, "Error saving to file", e)
            throw e
        }
    }

    /**
     * Validates the current switch configuration based on the scan mode.
     */
    fun isConfigInvalid(): String? {
        val preferenceManager = PreferenceManager(context)
        var mode =
            ScanMode(preferenceManager.getStringValue(PreferenceManager.PREFERENCE_KEY_SCAN_MODE))

        if (mode.id.isEmpty()) {
            mode = ScanMode(ScanMode.Modes.MODE_AUTO)
        }

        val containsSelect = switchEvents.any { it.containsAction(SwitchAction.ACTION_SELECT) }
        val containsNext =
            switchEvents.any { it.containsAction(SwitchAction.ACTION_MOVE_TO_NEXT_ITEM) }
        val containsPrevious =
            switchEvents.any { it.containsAction(SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM) }

        return when (mode.id) {
            ScanMode.Modes.MODE_AUTO -> {
                if (containsSelect) null
                else "At least one switch must be configured to the select action."
            }

            ScanMode.Modes.MODE_MANUAL -> {
                if (containsSelect && containsNext && containsPrevious) null
                else "At least one switch must be configured to the next, previous, and select actions."
            }

            else -> null
        }
    }

    /**
     * Reloads switch events from both local storage and Firestore.
     */
    fun reload() {
        readFile()
        pullEventsFromFirestore()
    }
}