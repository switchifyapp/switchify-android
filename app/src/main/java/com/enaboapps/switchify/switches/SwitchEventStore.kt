package com.enaboapps.switchify.switches

import android.content.Context
import android.content.Intent
import android.util.Log
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.scanning.ScanMode
import com.enaboapps.switchify.switches.profiles.SwitchProfileRepository
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @Volatile
    private var isInitialized = false

    private val tag = "SwitchEventStore"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val localStorage = SwitchEventLocalStorage()
    private var profileRepository: SwitchProfileRepository? = null

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

    @Volatile
    private var isInitializing = false

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized || isInitializing) {
            return
        }
        isInitializing = true
        profileRepository = SwitchProfileRepository.getInstance(context)

        coroutineScope.launch {
            val loadedEvents = localStorage.loadFromFile(context)
            switchEvents.clear()
            switchEvents.addAll(loadedEvents)
            isInitialized = true
            isInitializing = false
        }
    }

    /**
     * Suspending version of initialize that waits for loading to complete
     * before returning, ensuring switch events are fully loaded.
     */
    suspend fun initializeAsync(context: Context) {
        if (isInitialized) {
            return
        }

        profileRepository = SwitchProfileRepository.getInstance(context)
        val loadedEvents = localStorage.loadFromFile(context)
        switchEvents.clear()
        switchEvents.addAll(loadedEvents)
        isInitialized = true
    }

    fun getCount(): Int = switchEvents.size

    fun getSwitchEvents(profileId: String? = null): Set<SwitchEvent> {
        if (profileId == null || profileId == profileRepository?.document?.value?.activeProfileId) {
            return switchEvents.toSet()
        }
        return profileRepository?.events(profileId)?.toSet().orEmpty()
    }

    internal suspend fun refreshActiveProfile(context: Context) {
        val repository = profileRepository ?: SwitchProfileRepository.getInstance(context).also {
            profileRepository = it
        }
        repository.initialize()
        refreshActiveCache(repository)
    }

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
            val hasConflict = events.any { it.type == SWITCH_EVENT_TYPE_CAMERA && it.code == gestureId }
            if (hasConflict) {
                Logger.log(
                    LogEvent.SwitchConflictDetected,
                    data = mapOf(
                        "result" to "detected",
                        "gesture_id" to gestureId,
                        "source" to "read_only_check"
                    )
                )
            }
            hasConflict
        } catch (e: Exception) {
            Log.e(tag, "Error checking gesture conflict read-only", e)
            Logger.log(
                LogEvent.SwitchSaveFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "check_conflict_exception",
                    "gesture_id" to gestureId
                ),
                throwable = e
            )
            false
        }
    }

    fun find(code: String, profileId: String? = null): SwitchEvent? =
        getSwitchEvents(profileId).find { it.code == code }?.also {
            Log.d(tag, "Found switch event for code $code")
        } ?: run {
            Log.d(tag, "No switch event found for code $code")
            null
        }

    fun add(
        switchEvent: SwitchEvent,
        context: Context,
        profileId: String? = null,
        completion: ((Boolean) -> Unit)
    ) {
        coroutineScope.launch {
            val repository = profileRepository ?: SwitchProfileRepository.getInstance(context).also {
                profileRepository = it
                it.initialize()
            }
            val targetProfileId = profileId ?: repository.document.value.activeProfileId
            val events = repository.events(targetProfileId).toMutableList()
            val added = events.none { it.code == switchEvent.code }
            if (added) events.add(switchEvent)

            if (added) {
                if (repository.replaceEvents(targetProfileId, events)) {
                    refreshActiveCache(repository)
                    Log.d(tag, "Successfully added and saved switch event")
                    completion(true)
                    broadcastReloadEvent(context, targetProfileId == repository.document.value.activeProfileId)
                    Logger.log(LogEvent.SwitchAdded)
                } else {
                    Log.e(tag, "Failed to save switch event to file")
                    Logger.log(
                        LogEvent.SwitchSaveFailed,
                        data = mapOf(
                            "result" to "failure",
                            "reason" to "add_save_failed",
                            "switch_type" to switchEvent.type,
                            "switch_code" to switchEvent.code
                        )
                    )
                    completion(false)
                }
            } else {
                Log.e(tag, "Failed to add switch event to set")
                Logger.log(
                    LogEvent.SwitchSaveFailed,
                    data = mapOf(
                        "result" to "failure",
                        "reason" to "add_to_set_failed",
                        "switch_type" to switchEvent.type,
                        "switch_code" to switchEvent.code
                    )
                )
                completion(false)
            }
        }
    }

    fun update(
        switchEvent: SwitchEvent,
        context: Context,
        profileId: String? = null,
        completion: ((Boolean) -> Unit)
    ) = updateEvent(switchEvent.code, context, profileId, completion) { switchEvent }

    fun updateDetails(
        switchEvent: SwitchEvent,
        context: Context,
        profileId: String? = null,
        completion: (Boolean) -> Unit
    ) = updateEvent(switchEvent.code, context, profileId, completion) {
        it.copy(name = switchEvent.name, pressAction = switchEvent.pressAction)
    }

    fun updateHoldActions(
        code: String,
        actions: List<SwitchAction>,
        context: Context,
        profileId: String,
        completion: (Boolean) -> Unit
    ) = updateEvent(code, context, profileId, completion) { it.copy(holdActions = actions) }

    private fun updateEvent(
        code: String,
        context: Context,
        profileId: String?,
        completion: (Boolean) -> Unit,
        transform: (SwitchEvent) -> SwitchEvent
    ) {
        coroutineScope.launch {
            val repository = profileRepository ?: SwitchProfileRepository.getInstance(context).also {
                profileRepository = it
                it.initialize()
            }
            val targetProfileId = profileId ?: repository.document.value.activeProfileId
            val success = repository.updateEvent(targetProfileId, code, transform)
            if (success) {
                refreshActiveCache(repository)
                broadcastReloadEvent(context, targetProfileId == repository.document.value.activeProfileId)
                Logger.log(LogEvent.SwitchUpdated)
            } else {
                Logger.log(
                    LogEvent.SwitchSaveFailed,
                    data = mapOf(
                        "result" to "failure",
                        "reason" to "update_save_failed",
                        "switch_code" to code
                    )
                )
            }
            withContext(Dispatchers.Main) { completion(success) }
        }
    }

    fun remove(
        switchEvent: SwitchEvent,
        context: Context,
        profileId: String? = null,
        handler: ((Boolean) -> Unit)
    ) {
        coroutineScope.launch {
            val repository = profileRepository ?: SwitchProfileRepository.getInstance(context).also {
                profileRepository = it
                it.initialize()
            }
            val targetProfileId = profileId ?: repository.document.value.activeProfileId
            val events = repository.events(targetProfileId).toMutableList()
            val removed = events.removeIf { it.code == switchEvent.code }

            if (removed) {
                if (repository.replaceEvents(targetProfileId, events)) {
                    refreshActiveCache(repository)
                    Logger.log(LogEvent.SwitchRemoved)
                    broadcastReloadEvent(context, targetProfileId == repository.document.value.activeProfileId)
                    handler(true)
                } else {
                    Logger.log(
                        LogEvent.SwitchSaveFailed,
                        data = mapOf(
                            "result" to "failure",
                            "reason" to "remove_save_failed",
                            "switch_type" to switchEvent.type,
                            "switch_code" to switchEvent.code
                        )
                    )
                    handler(false)
                }
            } else {
                Logger.log(
                    LogEvent.SwitchSaveFailed,
                    data = mapOf(
                        "result" to "failure",
                        "reason" to "remove_not_found",
                        "switch_type" to switchEvent.type,
                        "switch_code" to switchEvent.code
                    )
                )
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
        val mode = ScanMode.fromId(
            preferenceManager.getStringValue(PreferenceManager.PREFERENCE_KEY_SCAN_MODE)
        )

        val configuredActions = SwitchHoldPolicy.configuredActionIds(
            switchEvents,
            SwitchHoldPolicy.isEnabled(preferenceManager)
        )
        val containsSelect = configuredActions.contains(SwitchAction.ACTION_SELECT)
        val containsNext = configuredActions.contains(SwitchAction.ACTION_MOVE_TO_NEXT_ITEM)
        val containsPrevious = configuredActions.contains(SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM)

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
    private fun refreshActiveCache(repository: SwitchProfileRepository) {
        replaceActiveProfileCache(repository.events())
    }

    internal fun replaceActiveProfileCache(events: List<SwitchEvent>) {
        synchronized(switchEvents) {
            switchEvents.clear()
            switchEvents.addAll(events)
        }
    }

    private fun broadcastReloadEvent(context: Context, activeChanged: Boolean) {
        ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.SwitchProfilesUpdated)
        if (activeChanged) {
            ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.SwitchEventsUpdated)
            context.sendBroadcast(Intent(EVENTS_UPDATED).setPackage(context.packageName))
        }
    }

}
