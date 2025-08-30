package com.enaboapps.switchify.service.utils

import android.content.Context
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Utility class to observe device lock/unlock state through polling.
 */
class DeviceLockObserver(private val context: Context) {

    companion object {
        private const val TAG = "DeviceLockObserver"
        private const val POLL_INTERVAL = 1000L // 1 second
    }

    private val userManager: UserManager by lazy {
        context.getSystemService(Context.USER_SERVICE) as UserManager
    }

    private var unlocked: Boolean = userManager.isUserUnlocked
        set(value) {
            if (field != value) {
                field = value
                when (value) {
                    true -> onDeviceUnlockedCallback?.invoke()
                    false -> onDeviceLockedCallback?.invoke()
                }
            }
        }

    private var onDeviceUnlockedCallback: (() -> Unit)? = null
    private var onDeviceLockedCallback: (() -> Unit)? = null
    private var pollingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Starts observing unlock events through polling
     */
    fun startObserving(
        onUnlocked: () -> Unit,
        onLocked: () -> Unit
    ) {
        onDeviceUnlockedCallback = onUnlocked
        onDeviceLockedCallback = onLocked

        // Initial state check
        checkAndUpdateUnlockState()

        // Start polling in background
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = coroutineScope.launch {
            while (isActive) {
                checkAndUpdateUnlockState()
                delay(POLL_INTERVAL)
            }
        }
    }

    private fun checkAndUpdateUnlockState() {
        unlocked = userManager.isUserUnlocked
        Log.d(TAG, "Device unlock state changed: $unlocked")
    }

    /**
     * Stops observing unlock events
     */
    fun stopObserving() {
        pollingJob?.cancel()
        pollingJob = null
        onDeviceUnlockedCallback = null
        onDeviceLockedCallback = null
    }

    /**
     * Checks if the user is currently unlocked.
     */
    fun isUserUnlocked(): Boolean = unlocked
}
