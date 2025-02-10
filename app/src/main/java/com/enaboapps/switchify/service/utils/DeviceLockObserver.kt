package com.enaboapps.switchify.service.utils

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.util.Log

/**
 * Utility class to observe both device lock/unlock state and user unlock events.
 */
class DeviceLockObserver(private val context: Context) {

    companion object {
        private const val TAG = "DeviceLockObserver"
    }

    private var onDeviceUnlockedCallback: (() -> Unit)? = null
    private var onDeviceLockedCallback: (() -> Unit)? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_UNLOCKED -> {
                    onDeviceUnlockedCallback?.invoke()
                }
            }
        }
    }

    /**
     * Starts observing unlock events
     */
    fun startObserving(onUnlocked: () -> Unit, onLocked: () -> Unit) {
        onDeviceUnlockedCallback = onUnlocked
        onDeviceLockedCallback = onLocked

        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        context.registerReceiver(unlockReceiver, filter)
    }

    /**
     * Stops observing unlock events and unregisters the receiver.
     */
    fun stopObserving() {
        try {
            context.unregisterReceiver(unlockReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        onDeviceUnlockedCallback = null
        onDeviceLockedCallback = null
    }

    /**
     * Checks if the user is currently unlocked.
     * Falls back to checking KeyguardManager if behavior is inconsistent.
     */
    fun isUserUnlocked(): Boolean {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // Some devices may report incorrectly, so check Keyguard as a fallback
        return userManager.isUserUnlocked && !keyguardManager.isDeviceLocked
    }

    /**
     * Checks if the device is currently locked (screen locked with PIN/Pattern).
     * @return true if locked, false otherwise.
     */
    fun isDeviceLocked(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isDeviceLocked = keyguardManager.isDeviceLocked
        Log.d(TAG, "Device locked: $isDeviceLocked")
        return isDeviceLocked
    }

    /**
     * Checks if the screen is currently locked by the keyguard.
     * @return true if the screen is locked, false otherwise.
     */
    fun isScreenLocked(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isKeyguardLocked
    }
}
