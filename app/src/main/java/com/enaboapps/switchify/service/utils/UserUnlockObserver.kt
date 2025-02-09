package com.enaboapps.switchify.service.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager

/**
 * A utility class that observes device unlock events.
 * This class registers a broadcast receiver to listen for USER_UNLOCKED events.
 */
class UserUnlockObserver(private val context: Context) {

    private var onUserUnlockedCallback: (() -> Unit)? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                onUserUnlockedCallback?.invoke()
            }
        }
    }

    /**
     * Starts observing device unlock events
     * @param onUserUnlocked callback to be invoked when the device is unlocked
     */
    fun startObserving(onUserUnlocked: () -> Unit) {
        onUserUnlockedCallback = onUserUnlocked
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        context.registerReceiver(unlockReceiver, filter)
    }

    /**
     * Stops observing device unlock events and cleans up resources
     */
    fun stopObserving() {
        try {
            context.unregisterReceiver(unlockReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        onUserUnlockedCallback = null
    }

    /**
     * Checks if the user is currently unlocked
     * @return true if the user is unlocked, false otherwise
     */
    fun isUserUnlocked(): Boolean {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        return userManager.isUserUnlocked
    }
} 