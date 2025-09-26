package com.enaboapps.switchify.switches

import android.content.Context
import android.content.Intent
import android.util.Log
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.scanning.ScanMode
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * SwitchEventStore manages the storage of switch events using local storage.
 *
 * Features:
 * - Local storage using JSON file
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

    /**
     * Suspending version of initialize that waits for loading to complete
     * before returning, ensuring switch events are fully loaded.
     */
    suspend fun initializeAsync(context: Context) {
        if (isInitialized) {
            return
        }

        val loadedEvents = localStorage.loadFromFile(context)
        switchEvents.clear()
        switchEvents.addAll(loadedEvents)
        isInitialized = true
    }

    fun getCount(): Int = switchEvents.size

    fun getSwitchEvents(): Set<SwitchEvent> = switchEvents.toSet()

    /**
     * Check if the store has been initialized and switch events loaded
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Read-only method to check if a specific gesture has a conflict with existing camera switches.
     * This method loads switch events directly from storage without triggering service notifications.
     * Used specifically for UI conflict detection to avoid unintended service activation.
     * 
     * @param context Application context
     * @param gestureId The facial gesture ID to check for conflicts
     * @return true if the gesture is assigned to a camera switch, false otherwise
     */
    suspend fun checkGestureConflictReadOnly(context: Context, gestureId: String): Boolean {
        return try {
            val events = localStorage.loadFromFile(context)
            events.any { it.type == SWITCH_EVENT_TYPE_CAMERA && it.code == gestureId }
        } catch (e: Exception) {
            Log.e(tag, "Error checking gesture conflict read-only", e)
            false
        }
    }

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
                    Logger.log(LogEvent.SwitchAdded)
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
                    Logger.log(LogEvent.SwitchUpdated)
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
                    Logger.log(LogEvent.SwitchRemoved)
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
     * Validates a switch event's data.
     *
     * @param switchEvent The switch event to validate
     * @return true if the switch event is valid, false otherwise
     */
    fun validateSwitchEvent(switchEvent: SwitchEvent): Boolean {
        val hasName = switchEvent.name.isNotBlank()
        val hasCode = switchEvent.code.isNotBlank()

        Log.d(tag, "Switch event validation - hasName: $hasName, hasCode: $hasCode")
        return hasName && hasCode
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
     * Notifies all listeners that switch events have been updated.
     * Uses hybrid approach: Flow for same-process, Broadcast for cross-process.
     */
    private fun broadcastReloadEvent(context: Context) {
        // Notify same-process listeners via ServiceBridge
        ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.SwitchEventsUpdated)

        // Notify cross-process listeners (e.g., accessibility service)
        context.sendBroadcast(Intent(EVENTS_UPDATED).setPackage(context.packageName))
    }

}