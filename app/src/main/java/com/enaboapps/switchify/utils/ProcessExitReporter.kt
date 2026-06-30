package com.enaboapps.switchify.utils

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.backend.preferences.PreferenceManager

internal object ProcessExitReporter {
    fun reportRecentExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !Logger.isTelemetryEnabled()) return
        val appContext = context.applicationContext
        val preferenceManager = PreferenceManager(appContext)
        val lastTimestamp = preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_LAST_PROCESS_EXIT_TIMESTAMP,
            0L
        )
        val activityManager = appContext.getSystemService(ActivityManager::class.java) ?: return
        val exits = activityManager.getHistoricalProcessExitReasons(appContext.packageName, 0, 10)
            .filter { isActionableReason(it.reason) && it.timestamp > lastTimestamp }
            .sortedBy { it.timestamp }

        var newestTimestamp = lastTimestamp
        exits.forEach { exit ->
            val uploaded = Logger.logNow(
                event = LogEvent.ProcessExitDetected,
                data = mapOf(
                    "reason" to reasonName(exit.reason),
                    "reasonCode" to exit.reason,
                    "status" to exit.status,
                    "importance" to exit.importance,
                    "exitTimestamp" to exit.timestamp,
                    "processName" to exit.processName,
                    "description" to (exit.description ?: ""),
                    "versionName" to BuildConfig.VERSION_NAME,
                    "versionCode" to BuildConfig.VERSION_CODE.toLong()
                )
            )
            if (uploaded && exit.timestamp > newestTimestamp) {
                newestTimestamp = exit.timestamp
            }
        }

        if (newestTimestamp > lastTimestamp) {
            preferenceManager.setLongValue(
                PreferenceManager.PREFERENCE_KEY_LAST_PROCESS_EXIT_TIMESTAMP,
                newestTimestamp
            )
        }
    }

    internal fun isActionableReason(reason: Int): Boolean {
        return isActionableReason(reason, Build.VERSION.SDK_INT)
    }

    internal fun isActionableReason(reason: Int, sdkInt: Int): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.R) {
            reason == ApplicationExitInfo.REASON_ANR ||
                reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
                reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ||
                reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ||
                reason == ApplicationExitInfo.REASON_SIGNALED
        } else {
            false
        }
    }

    internal fun reasonName(reason: Int): String {
        return reasonName(reason, Build.VERSION.SDK_INT)
    }

    internal fun reasonName(reason: Int, sdkInt: Int): String {
        if (sdkInt < Build.VERSION_CODES.R) return "UNSUPPORTED"
        return when (reason) {
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
            else -> "OTHER"
        }
    }
}
