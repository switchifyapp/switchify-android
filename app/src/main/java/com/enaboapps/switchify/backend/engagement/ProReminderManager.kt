package com.enaboapps.switchify.backend.engagement

import android.content.Context
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Manages periodic reminders to encourage Pro upgrades for non-Pro users.
 * Uses a balanced, non-intrusive approach:
 * - Tracks distinct usage days (not elapsed time)
 * - Shows reminders after 3 days of usage
 * - Allows dismissal with configurable cooldown periods
 * - Maximum 3 reminders total, then stops showing
 */
class ProReminderManager(context: Context) {
    private val preferenceManager = PreferenceManager(context)

    companion object {
        private const val DAYS_THRESHOLD = 3 // Show after 3 distinct usage days
        private const val MAX_REMINDERS = 3 // Maximum number of reminders to show
        private const val DEFAULT_COOLDOWN_DAYS = 7 // Default cooldown after dismiss
        private const val REMIND_LATER_COOLDOWN_DAYS = 3 // Shorter cooldown for "remind later"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /**
     * Records that the user opened the app today.
     * Call this from HomeScreen on each visit.
     */
    fun recordAppOpen() {
        val today = LocalDate.now().format(DATE_FORMAT)
        val lastDate = preferenceManager.getStringValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_LAST_USAGE_DATE,
            ""
        )

        if (today != lastDate) {
            // New day - increment usage count
            val currentDays = preferenceManager.getIntegerValue(
                PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_USAGE_DAYS,
                0
            )
            preferenceManager.setIntegerValue(
                PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_USAGE_DAYS,
                currentDays + 1
            )
            preferenceManager.setStringValue(
                PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_LAST_USAGE_DATE,
                today
            )
        }
    }

    /**
     * Checks if the Pro reminder should be shown.
     * @return true if reminder should be displayed
     */
    fun shouldShowReminder(): Boolean {
        // Never show for Pro users
        if (IAPHandler.hasPurchasedPro()) return false

        // Check if max reminders reached
        val dismissCount = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_DISMISS_COUNT,
            0
        )
        if (dismissCount >= MAX_REMINDERS) return false

        // Check cooldown period
        val lastDismissTime = preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_LAST_DISMISS_TIME,
            0L
        )
        val cooldownDays = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_COOLDOWN_DAYS,
            DEFAULT_COOLDOWN_DAYS
        )
        val daysSinceLastDismiss = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - lastDismissTime
        )
        if (lastDismissTime > 0 && daysSinceLastDismiss < cooldownDays) return false

        // Check usage threshold
        val usageDays = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_USAGE_DAYS,
            0
        )
        return usageDays >= DAYS_THRESHOLD
    }

    /**
     * Called when user dismisses the reminder.
     * Sets a 7-day cooldown before showing again.
     */
    fun onDismiss() {
        incrementDismissCount()
        setCooldown(DEFAULT_COOLDOWN_DAYS)
    }

    /**
     * Called when user chooses "remind me later".
     * Sets a 3-day cooldown before showing again.
     */
    fun onRemindLater() {
        setCooldown(REMIND_LATER_COOLDOWN_DAYS)
    }

    private fun incrementDismissCount() {
        val currentCount = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_DISMISS_COUNT,
            0
        )
        preferenceManager.setIntegerValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_DISMISS_COUNT,
            currentCount + 1
        )
    }

    private fun setCooldown(days: Int) {
        preferenceManager.setLongValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_LAST_DISMISS_TIME,
            System.currentTimeMillis()
        )
        preferenceManager.setIntegerValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_COOLDOWN_DAYS,
            days
        )
    }

    /**
     * Gets the number of distinct usage days.
     */
    fun getUsageDays(): Int {
        return preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_USAGE_DAYS,
            0
        )
    }

    /**
     * Gets the number of times the reminder has been dismissed.
     */
    fun getDismissCount(): Int {
        return preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_PRO_REMINDER_DISMISS_COUNT,
            0
        )
    }
}
