package com.enaboapps.switchify.switches

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.scanning.ScanMode
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
 * - Broadcasts an event to all listeners when switch events are updated
 */
class SwitchEventStore private constructor() {
    // Core data storage - using thread-safe set
    private val switchEvents = Collections.synchronizedSet(mutableSetOf<SwitchEvent>())
    private var isInitialized = false

    private val tag = "SwitchEventStore"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val localStorage = SwitchEventLocalStorage()
    private val firestore = SwitchEventFirestore()

    companion object {
        const val EVENTS_UPDATED = "com.enaboapps.switchify.EVENTS_UPDATED"

        @Volatile
        private var instance: SwitchEventStore? = null

        fun getInstance(): SwitchEventStore {
            return instance ?: synchronized(this) {
                instance ?: SwitchEventStore().also { instance = it }
            }
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) {
            return
        }

        coroutineScope.launch {
            val loadedEvents = localStorage.loadFromFile(context)
            switchEvents.clear()
            switchEvents.addAll(loadedEvents)
        }
        isInitialized = true
    }

    fun getCount(): Int = switchEvents.size

    fun getSwitchEvents(): Set<SwitchEvent> = switchEvents.toSet()

    fun find(code: String): SwitchEvent? =
        switchEvents.find { it.code == code }?.also {
            Log.d(tag, "Found switch event for code $code")
        } ?: run {
            Log.d(tag, "No switch event found for code $code")
            null
        }

    fun add(switchEvent: SwitchEvent, context: Context, completion: ((Boolean) -> Unit)) {
        coroutineScope.launch {
            val added = switchEvents.add(switchEvent)

            if (added) {
                if (localStorage.saveToFile(context, switchEvents)) {
                    Log.d(tag, "Successfully added and saved switch event")
                    completion(true)
                    broadcastReloadEvent(context)
                    if (firestore.pushEvent(switchEvent)) {
                        Logger.logEvent("Added switch: ${switchEvent.name}")
                    }
                } else {
                    Log.e(tag, "Failed to save switch event to file")
                    switchEvents.remove(switchEvent)
                    completion(false)
                }
            } else {
                Log.e(tag, "Failed to add switch event to set")
                completion(false)
            }
        }
    }

    fun update(switchEvent: SwitchEvent, context: Context, completion: ((Boolean) -> Unit)) {
        coroutineScope.launch {
            var updated = switchEvents.removeIf { it.code == switchEvent.code }
            if (updated) {
                updated = switchEvents.add(switchEvent)
            }

            if (updated) {
                if (localStorage.saveToFile(context, switchEvents)) {
                    completion(true)
                    broadcastReloadEvent(context)
                    if (firestore.pushEvent(switchEvent)) {
                        Logger.logEvent("Updated switch: ${switchEvent.name}")
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
            val removed = switchEvents.removeIf { it.code == switchEvent.code }

            if (removed) {
                if (localStorage.saveToFile(context, switchEvents)) {
                    Logger.logEvent("Removed switch: ${switchEvent.name}")
                    broadcastReloadEvent(context)
                    handler(true)
                } else {
                    switchEvents.add(switchEvent)
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
        return withContext(Dispatchers.IO) {
            try {
                val remoteEvents = firestore.pullEvents() ?: return@withContext Result.failure(
                    Exception("Failed to fetch remote switches")
                )

                val switches = remoteEvents.map { event ->
                    RemoteSwitchInfo(
                        type = event.type,
                        name = event.name,
                        code = event.code,
                        isOnDevice = switchEvents.any { it.code == event.code }
                    )
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
        if (switchEvents.any { it.code == code }) {
            Log.d(tag, "Switch with code $code is already on device")
            return Result.failure(Exception("Switch is already on device"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val switchEvent = firestore.fetchEvent(code) ?: return@withContext Result.failure(
                    Exception("Switch not found")
                )

                val added = switchEvents.add(switchEvent)

                if (!added) {
                    return@withContext Result.failure(Exception("Failed to add switch"))
                }

                if (localStorage.saveToFile(context, switchEvents)) {
                    Log.d(tag, "Successfully imported switch: ${switchEvent.name}")
                    Logger.logEvent("Imported switch: $code")
                    broadcastReloadEvent(context)
                    Result.success(switchEvent)
                } else {
                    Result.failure(Exception("Failed to write to file"))
                }
            } catch (e: Exception) {
                Logger.logEvent("Error importing switch: $code, ${e.message}")
                Result.failure(e)
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
     * Broadcasts an event to all listeners to reload switch events.
     */
    private fun broadcastReloadEvent(context: Context) {
        val localBroadcastManager = LocalBroadcastManager.getInstance(context)
        localBroadcastManager.sendBroadcast(Intent(EVENTS_UPDATED))
    }

    /**
     * Removes a remote switch from Firestore.
     *
     * @param code The code of the switch to remove
     * @param context The application context
     * @return Result indicating success or failure
     */
    suspend fun removeRemote(code: String, context: Context): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (firestore.removeEvent(code)) {
                Log.d(tag, "Successfully removed remote switch: $code")
                broadcastReloadEvent(context)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove remote switch"))
            }
        }
    }
}