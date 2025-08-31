package com.enaboapps.switchify.service.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DeviceLockObserver(context: Context) {

    companion object {
        @JvmStatic
        fun isUserUnlocked(context: Context): Boolean {
            val um = context.applicationContext.getSystemService(Context.USER_SERVICE) as UserManager
            return um.isUserUnlocked
        }
    }

    private val appContext = context.applicationContext
    @Volatile private var unlocked: Boolean = isUserUnlocked(appContext)

    private var onDeviceUnlockedCallback: (() -> Unit)? = null
    private var onDeviceLockedCallback: (() -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                if (!unlocked) {
                    unlocked = true
                    dispatchUnlocked()
                }
            }
        }
    }

    fun startObserving(
        onUnlocked: () -> Unit,
        onLocked: () -> Unit
    ) {
        onDeviceUnlockedCallback = onUnlocked
        onDeviceLockedCallback = onLocked

        // Initial state notify if already unlocked
        if (unlocked) dispatchUnlocked()

        // Register for unlock broadcasts
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        appContext.registerReceiver(receiver, filter)
    }

    fun stopObserving() {
        runCatching { appContext.unregisterReceiver(receiver) }
        onDeviceUnlockedCallback = null
        onDeviceLockedCallback = null
        scope.cancel()
    }

    fun isUserUnlocked(): Boolean = unlocked

    private fun dispatchUnlocked() {
        val cb = onDeviceUnlockedCallback ?: return
        scope.launch(Dispatchers.Main) { cb.invoke() }
    }
}
