package com.enaboapps.switchify.switches

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.enaboapps.switchify.auth.AuthManager
import com.enaboapps.switchify.backend.data.FileManager
import com.enaboapps.switchify.backend.data.FirestoreManager
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.scanning.ScanMode
import com.enaboapps.switchify.utils.Logger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections

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
 * @property localOnly Whether to only use local storage (no cloud synchronization)
 */
class SwitchEventStore private constructor() {
    // Core data storage - using thread-safe set
    private val switchEvents = Collections.synchronizedSet(mutableSetOf<SwitchEvent>())
    private val fileName = "switch_events.json"
    private var localOnly: Boolean = false
    private var isInitialized = false

    // Utilities and managers
    private val gson = Gson()
    private val tag = "SwitchEventStore"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val fileLock = Mutex() // Only used for file operations

    companion object {
        const val EVENTS_UPDATED = "com.enaboapps.switchify.EVENTS_UPDATED"
        private const val COLLECTION_USER_SWITCHES = "user-switches"
        private const val SWITCHES_COLLECTION = "switches"

        @Volatile
        private var instance: SwitchEventStore? = null

        fun getInstance(): SwitchEventStore {
            return instance ?: synchronized(this) {
                instance ?: SwitchEventStore().also { instance = it }
            }
        }
    }

    @Synchronized
    fun initialize(context: Context, localOnly: Boolean = false) {
        if (isInitialized && this.localOnly == localOnly) {
            return
        }

        this.localOnly = localOnly
        coroutineScope.launch {
            loadFromFile(context)
            if (!localOnly) {
                pullEventsFromFirestore()
            }
        }
        isInitialized = true
    }

    fun getCount(): Int = synchronized(switchEvents) { switchEvents.size }

    private suspend fun loadFromFile(context: Context) {
        fileLock.withLock {
            try {
                val fileManager = FileManager.create(context)
                val type = object : TypeToken<Set<SwitchEvent>>() {}.type
                fileManager.readJson<Set<SwitchEvent>>(fileName, type)
                    .onSuccess { events ->
                        synchronized(switchEvents) {
                            switchEvents.clear()
                            switchEvents.addAll(events)
                            switchEvents.forEach { it.isOnDevice = true }
                            switchEvents.forEach { it.log() }
                        }
                    }
                    .onFailure { error ->
                        Log.e(tag, "Error reading from file", error)
                        deleteFile(context)
                    }
            } catch (e: Exception) {
                Log.e(tag, "Error reading from file", e)
                deleteFile(context)
            }
        }
    }

    private suspend fun saveToFile(context: Context): Boolean {
        return fileLock.withLock {
            try {
                val fileManager = FileManager.create(context)
                val eventsToSave = synchronized(switchEvents) { switchEvents.toSet() }
                fileManager.writeJson<Set<SwitchEvent>>(fileName, eventsToSave)
                    .onSuccess {
                        LocalBroadcastManager.getInstance(context)
                            .sendBroadcast(Intent(EVENTS_UPDATED))
                        true
                    }
                    .onFailure { error ->
                        Log.e(tag, "Error saving to file", error)
                        false
                    }
                    .isSuccess
            } catch (e: Exception) {
                Log.e(tag, "Error saving to file", e)
                false
            }
        }
    }

    private fun deleteFile(context: Context) {
        coroutineScope.launch {
            FileManager.create(context).deleteFile(fileName)
        }
    }

    fun getSwitchEvents(): Set<SwitchEvent> = synchronized(switchEvents) { switchEvents.toSet() }

    fun find(code: String): SwitchEvent? =
        synchronized(switchEvents) {
            switchEvents.find { it.code == code }?.also {
                Log.d(tag, "Found switch event for code $code")
            } ?: run {
                Log.d(tag, "No switch event found for code $code")
                null
            }
        }

    fun add(switchEvent: SwitchEvent, context: Context, completion: ((Boolean) -> Unit)) {
        coroutineScope.launch {
            var added = false
            synchronized(switchEvents) {
                added = switchEvents.add(switchEvent)
            }

            if (added) {
                if (saveToFile(context)) {
                    completion(true)
                    if (!localOnly) {
                        val path = getSwitchPath(switchEvent.code)
                        if (path.isNotEmpty()) {
                            FirestoreManager.getInstance().saveDocument(
                                path = path,
                                data = switchEvent.toMap()
                            )
                            Logger.logEvent("Added switch: ${switchEvent.name}")
                        }
                    }
                } else {
                    synchronized(switchEvents) {
                        switchEvents.remove(switchEvent)
                    }
                    completion(false)
                }
            } else {
                completion(false)
            }
        }
    }

    fun update(switchEvent: SwitchEvent, context: Context, completion: ((Boolean) -> Unit)) {
        coroutineScope.launch {
            var updated = false
            synchronized(switchEvents) {
                updated = switchEvents.removeIf { it.code == switchEvent.code } &&
                        switchEvents.add(switchEvent)
            }

            if (updated) {
                if (saveToFile(context)) {
                    completion(true)
                    if (!localOnly) {
                        val path = getSwitchPath(switchEvent.code)
                        if (path.isNotEmpty()) {
                            FirestoreManager.getInstance().saveDocument(
                                path = path,
                                data = switchEvent.toMap()
                            )
                            Logger.logEvent("Updated switch: ${switchEvent.name}")
                        }
                    }
                } else {
                    completion(false)
                }
            } else {
                completion(false)
            }
        }
    }

    fun remove(switchEvent: SwitchEvent, context: Context, handler: ((Boolean) -> Unit)) {
        coroutineScope.launch {
            var removed = false
            synchronized(switchEvents) {
                removed = switchEvents.removeIf { it.code == switchEvent.code }
            }

            if (removed) {
                if (saveToFile(context)) {
                    Logger.logEvent("Removed switch: ${switchEvent.name}")
                    handler(true)
                } else {
                    synchronized(switchEvents) {
                        switchEvents.add(switchEvent)
                    }
                    handler(false)
                }
            } else {
                handler(false)
            }
        }
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
     * @param context The context used for file operations
     * @return Result containing the imported SwitchEvent if successful
     */
    suspend fun importSwitch(code: String, context: Context): Result<SwitchEvent> {
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
                ) ?: return@withContext Result.failure(Exception("Switch not found"))

                val switchEvent = gson.fromJson<SwitchEvent>(
                    gson.toJson(document),
                    SwitchEvent::class.java
                )

                var added = false
                synchronized(switchEvents) {
                    added = switchEvents.add(switchEvent)
                }

                if (!added) {
                    return@withContext Result.failure(Exception("Failed to add switch"))
                }

                if (saveToFile(context)) {
                    Log.d(tag, "Successfully imported switch: ${switchEvent.name}")
                    Logger.logEvent("Imported switch: $code")
                    Result.success(switchEvent)
                } else {
                    synchronized(switchEvents) {
                        switchEvents.remove(switchEvent)
                    }
                    Result.failure(Exception("Failed to write to file"))
                }
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
        if (localOnly) return

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

                if (remoteEvents.isEmpty()) {
                    Log.i(tag, "No switch events found in Firestore")
                    if (switchEvents.isNotEmpty()) {
                        pushEventsToFirestore()
                    }
                    return@launch
                }

                synchronized(switchEvents) {
                    // First remove any events that are no longer in the remote set
                    switchEvents.removeIf { local ->
                        remoteEvents.none { remote -> remote.code == local.code }
                    }
                    // Then add or update events from remote
                    remoteEvents.forEach { remote ->
                        switchEvents.removeIf { it.code == remote.code }
                        switchEvents.add(remote)
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
        if (localOnly) return

        coroutineScope.launch {
            try {
                val eventsToSync = synchronized(switchEvents) { switchEvents.toSet() }

                eventsToSync.forEach { event ->
                    val path = getSwitchPath(event.code)
                    if (path.isNotEmpty()) {
                        FirestoreManager.getInstance().saveDocument(
                            path = path,
                            data = event.toMap()
                        )
                    }
                }

                Log.i(tag, "Successfully pushed ${eventsToSync.size} switch events to Firestore")
                Logger.logEvent("Pushed ${eventsToSync.size} switch events to Firestore")
            } catch (e: Exception) {
                Logger.logEvent("Error pushing to Firestore: ${e.message}")
            }
        }
    }

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
     * Validates the current switch configuration based on the scan mode.
     */
    fun isConfigInvalid(context: Context): String? {
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
    fun reload(context: Context) {
        coroutineScope.launch {
            loadFromFile(context)
            if (!localOnly) {
                pullEventsFromFirestore()
            }
        }
    }

    /**
     * Removes a remote switch from Firestore.
     *
     * @param code The code of the switch to remove
     * @return Result indicating success or failure
     */
    suspend fun removeRemote(code: String): Result<Unit> {
        val userId = AuthManager.instance.getUserId() ?: run {
            Log.e(tag, "Could not get user ID")
            return Result.failure(Exception("User ID not available"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val path = "$COLLECTION_USER_SWITCHES/$userId/$SWITCHES_COLLECTION/$code"
                FirestoreManager.getInstance().deleteDocument(path)
                Log.d(tag, "Successfully removed remote switch: $code")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "Error removing remote switch: ${e.message}")
                Result.failure(e)
            }
        }
    }
}