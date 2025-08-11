package com.enaboapps.switchify.backend.preferences

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages queuing and batching of preference sync operations.
 * Implements debouncing with a 3-second delay to avoid excessive network calls.
 */
class SyncQueue private constructor() {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingChanges = ConcurrentHashMap<String, Any>()
    private val currentSyncJob = AtomicReference<Job?>(null)
    
    companion object {
        private const val TAG = "SyncQueue"
        private const val SYNC_DELAY_MS = 3000L
        
        @Volatile
        private var instance: SyncQueue? = null
        
        fun getInstance(): SyncQueue {
            return instance ?: synchronized(this) {
                instance ?: SyncQueue().also { instance = it }
            }
        }
    }
    
    /**
     * Queues a preference change for sync. Cancels previous sync job and starts a new one
     * with 3-second delay.
     */
    fun queueChange(key: String, value: Any) {
        Log.d(TAG, "Queueing change for key: $key")
        
        // Add to pending changes
        pendingChanges[key] = value
        
        // Cancel previous sync job
        currentSyncJob.get()?.cancel()
        
        // Start new debounced sync job
        val newJob = coroutineScope.launch {
            delay(SYNC_DELAY_MS)
            performBatchedSync()
        }
        
        currentSyncJob.set(newJob)
    }
    
    /**
     * Forces immediate sync of all pending changes without delay.
     */
    fun forceSyncNow() {
        Log.d(TAG, "Forcing immediate sync")
        
        // Cancel pending job
        currentSyncJob.get()?.cancel()
        
        // Sync immediately
        coroutineScope.launch {
            performBatchedSync()
        }
    }
    
    /**
     * Performs batched sync of all pending changes.
     */
    private suspend fun performBatchedSync() {
        if (pendingChanges.isEmpty()) {
            Log.d(TAG, "No pending changes to sync")
            return
        }
        
        try {
            // Create snapshot of pending changes
            val changesToSync = pendingChanges.toMap()
            Log.i(TAG, "Syncing ${changesToSync.size} batched changes")
            
            // Get current sync instance and perform upload
            val result = PreferenceSync.getInstance().uploadBatchedChanges(changesToSync)
            
            result.fold(
                onSuccess = {
                    // Remove successfully synced changes
                    changesToSync.keys.forEach { key ->
                        pendingChanges.remove(key, changesToSync[key])
                    }
                    Log.i(TAG, "Batched sync completed successfully")
                },
                onFailure = { e ->
                    Log.e(TAG, "Batched sync failed", e)
                    // Changes remain in queue for retry
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during batched sync", e)
        }
    }
    
    /**
     * Clears all pending changes (useful for logout scenarios).
     */
    fun clearQueue() {
        Log.d(TAG, "Clearing sync queue")
        currentSyncJob.get()?.cancel()
        pendingChanges.clear()
    }
    
    /**
     * Returns the number of pending changes.
     */
    fun getPendingCount(): Int = pendingChanges.size
}