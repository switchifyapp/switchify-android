package com.enaboapps.switchify.switches

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.backend.data.FileManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles local file storage operations for switch events.
 * This includes saving and loading switch events from the device's local storage.
 */
class SwitchEventLocalStorage {
    private val fileName = "switch_events.json"
    private val tag = "SwitchEventLocalStorage"
    private val gson = Gson()
    private val fileLock = Mutex()

    /**
     * Loads switch events from local file storage.
     *
     * @param context Application context
     * @return Set of loaded switch events
     */
    suspend fun loadFromFile(context: Context): Set<SwitchEvent> {
        return fileLock.withLock {
            try {
                val fileManager = FileManager.create(context)
                val type = object : TypeToken<Set<SwitchEvent>>() {}.type
                Log.d(tag, "Attempting to load switch events from file")
                fileManager.readJson<Set<SwitchEvent>>(fileName, type)
                    .onSuccess { events ->
                        Log.d(tag, "Loaded ${events.size} switch events from file")
                        events.forEach { it.isOnDevice = true }
                        events.forEach { it.log() }
                        return@withLock events
                    }
                    .onFailure { error ->
                        Log.e(tag, "Error reading from file", error)
                        deleteFile(context)
                    }
                emptySet()
            } catch (e: Exception) {
                Log.e(tag, "Error reading from file", e)
                deleteFile(context)
                emptySet()
            }
        }
    }

    /**
     * Saves switch events to local file storage.
     *
     * @param context Application context
     * @param switchEvents Set of switch events to save
     * @return Boolean indicating success or failure
     */
    suspend fun saveToFile(context: Context, switchEvents: Set<SwitchEvent>): Boolean {
        return fileLock.withLock {
            try {
                val fileManager = FileManager.create(context)
                val onDeviceEvents = switchEvents.filter { it.isOnDevice }.toSet()
                Log.d(tag, "Attempting to save ${onDeviceEvents.size} switch events to file")
                Log.d(tag, "Events to save: ${gson.toJson(onDeviceEvents)}")

                fileManager.writeJson<Set<SwitchEvent>>(fileName, onDeviceEvents)
                    .onSuccess {
                        Log.d(tag, "Successfully saved switch events to file")
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

    private suspend fun deleteFile(context: Context) {
        FileManager.create(context).deleteFile(fileName)
    }
} 