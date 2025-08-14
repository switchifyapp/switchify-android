package com.enaboapps.switchify.service.trial

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger

/**
 * Manages the trial period for non-pro users of the accessibility service.
 * Pro users have unlimited access. Non-pro users get fresh trials with unlimited restarts.
 * 
 * Trial Duration:
 * - Release builds: 1-hour trials
 * - Debug builds: 30-second trials for testing
 */
class ServiceTrialManager(
    private val context: Context,
    private val onTrialExpired: () -> Unit
) {
    private val preferenceManager = PreferenceManager(context)
    companion object {
        private const val TAG = "ServiceTrialManager"
        private const val TRIAL_DURATION_MS = 3600000L // 1 hour in milliseconds
        private const val WARNING_TIME_MS = 3000000L // 50 minutes into trial (10 minutes before expiry)
        
        // Debug mode for testing - reduces trial to 30 seconds
        private const val DEBUG_TRIAL_DURATION_MS = 30000L // 30 seconds
        private const val DEBUG_WARNING_TIME_MS = 20000L // 10 seconds before expiry
        
    }

    private var trialStartTime: Long = 0
    private var isTrialActive: Boolean = false
    private var warningHandler: Handler? = null
    private var expiryHandler: Handler? = null

    /**
     * Gets whether trials are disabled for debugging
     */
    fun isTrialDisabled(): Boolean {
        return preferenceManager.getBooleanValue(
            PreferenceManager.PREFERENCE_KEY_DEBUG_TRIAL_DISABLED,
            false
        )
    }

    /**
     * Sets whether trials are disabled for debugging
     */
    fun setTrialDisabled(disabled: Boolean) {
        preferenceManager.setBooleanValue(
            PreferenceManager.PREFERENCE_KEY_DEBUG_TRIAL_DISABLED,
            disabled
        )
    }

    /**
     * Starts the trial period for this service session.
     * Only applies to non-pro users. Pro users have unlimited access.
     * Called when the accessibility service connects.
     */
    fun startTrial() {
        // Check if trials are disabled for debugging
        if (isTrialDisabled()) {
            Log.d(TAG, "Trials disabled for debugging - unlimited access")
            ServiceMessageHUD.instance.showMessage(
                R.string.debug_trial_disabled_message,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
            return
        }

        // Check if user has pro - if so, no trial needed
        if (IAPHandler.hasPurchasedPro()) {
            Log.d(TAG, "User has pro - no trial restrictions")
            ServiceMessageHUD.instance.showMessage(
                R.string.pro_unlimited_access_message,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
            return
        }

        if (isTrialActive) {
            Log.w(TAG, "Trial already active")
            return
        }

        trialStartTime = System.currentTimeMillis()
        isTrialActive = true

        val durationText = if (BuildConfig.DEBUG) "30-second debug" else "1-hour"
        Log.d(TAG, "Starting $durationText service trial for non-pro user")
        Logger.log(LogEvent.ServiceConnected) // Reuse existing event

        scheduleWarning()
        scheduleExpiry()

        val messageRes = if (BuildConfig.DEBUG) R.string.debug_trial_started_message else R.string.trial_started_message
        ServiceMessageHUD.instance.showMessage(
            messageRes,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )
    }

    /**
     * Stops the trial and cleans up timers.
     * Called when the accessibility service disconnects.
     */
    fun stopTrial() {
        isTrialActive = false
        cancelTimers()
        Log.d(TAG, "Trial stopped")
    }

    /**
     * Gets the remaining trial time in milliseconds.
     * @return Remaining time in ms, or 0 if trial expired or not active
     */
    fun getRemainingTime(): Long {
        if (!isTrialActive) return 0
        
        val elapsed = System.currentTimeMillis() - trialStartTime
        val duration = if (BuildConfig.DEBUG) DEBUG_TRIAL_DURATION_MS else TRIAL_DURATION_MS
        val remaining = duration - elapsed
        return maxOf(0, remaining)
    }

    /**
     * Gets the remaining trial time formatted as a string.
     * @return Formatted time string (e.g., "45:30")
     */
    fun getRemainingTimeFormatted(): String {
        val remainingMs = getRemainingTime()
        val totalMinutes = (remainingMs / 60000).toInt()
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        
        return if (hours > 0) {
            String.format("%d:%02d", hours, minutes)
        } else {
            String.format("%d min", minutes)
        }
    }

    /**
     * Checks if the trial is currently active.
     * Pro users and disabled trials are never in trial mode.
     * @return true if trial is active for non-pro user, false otherwise
     */
    fun isTrialActive(): Boolean {
        // Disabled trials don't have restrictions
        if (isTrialDisabled()) {
            return false
        }
        // Pro users don't have trial restrictions
        if (IAPHandler.hasPurchasedPro()) {
            return false
        }
        return isTrialActive && getRemainingTime() > 0
    }

    /**
     * Schedules the warning before trial expiry.
     */
    private fun scheduleWarning() {
        warningHandler = Handler(Looper.getMainLooper())
        val warningTime = if (BuildConfig.DEBUG) DEBUG_WARNING_TIME_MS else WARNING_TIME_MS
        warningHandler?.postDelayed({
            if (isTrialActive) {
                ServiceMessageHUD.instance.showMessage(
                    R.string.trial_warning_message,
                    ServiceMessageHUD.MessageType.PERMANENT
                )
                val timeText = if (BuildConfig.DEBUG) "10 seconds" else "10 minutes"
                Log.d(TAG, "Trial warning shown - $timeText remaining")
            }
        }, warningTime)
    }

    /**
     * Schedules the trial expiry.
     */
    private fun scheduleExpiry() {
        expiryHandler = Handler(Looper.getMainLooper())
        val duration = if (BuildConfig.DEBUG) DEBUG_TRIAL_DURATION_MS else TRIAL_DURATION_MS
        expiryHandler?.postDelayed({
            if (isTrialActive) {
                Log.d(TAG, "Trial expired - shutting down service")
                ServiceMessageHUD.instance.showMessage(
                    R.string.trial_expired_message,
                    ServiceMessageHUD.MessageType.PERMANENT
                )
                isTrialActive = false
                onTrialExpired()
            }
        }, duration)
    }

    /**
     * Cancels all scheduled timers.
     */
    private fun cancelTimers() {
        warningHandler?.removeCallbacksAndMessages(null)
        expiryHandler?.removeCallbacksAndMessages(null)
        warningHandler = null
        expiryHandler = null
    }
}