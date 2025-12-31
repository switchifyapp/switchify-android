package com.enaboapps.switchify.service.stats

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import java.util.concurrent.TimeUnit

/**
 * Scheduler for stats aggregation background work.
 * Schedules daily aggregation to run when device is idle.
 */
object StatsAggregationScheduler {
    private const val TAG = "StatsAggregationScheduler"

    /**
     * Schedules daily stats aggregation.
     * Runs once per day with flexible timing (between 1-2 AM typically).
     * CRITICAL: Only schedules if device is unlocked to prevent WorkManager crash.
     */
    fun scheduleDailyAggregation(context: Context) {
        // WorkManager requires database access, which will crash if device is locked
        if (!DeviceLockObserver.isUserUnlocked(context)) {
            Log.w(TAG, "Device is locked, deferring stats aggregation scheduling")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)  // Only run when battery is not low
            .build()

        val aggregationWork = PeriodicWorkRequestBuilder<StatsAggregationWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)  // Wait 1 hour after app start
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            StatsAggregationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,  // Keep existing work if already scheduled
            aggregationWork
        )

        Log.i(TAG, "Daily stats aggregation scheduled")
    }

    /**
     * Triggers an immediate aggregation (for testing or manual trigger).
     */
    fun triggerImmediateAggregation(context: Context) {
        if (!DeviceLockObserver.isUserUnlocked(context)) {
            Log.w(TAG, "Device is locked, cannot trigger immediate aggregation")
            return
        }

        val aggregationWork = androidx.work.OneTimeWorkRequestBuilder<StatsAggregationWorker>()
            .build()

        WorkManager.getInstance(context).enqueue(aggregationWork)

        Log.i(TAG, "Immediate stats aggregation triggered")
    }

    /**
     * Cancels the scheduled aggregation work.
     */
    fun cancelAggregation(context: Context) {
        if (!DeviceLockObserver.isUserUnlocked(context)) {
            Log.w(TAG, "Device is locked, cannot cancel aggregation")
            return
        }

        WorkManager.getInstance(context).cancelUniqueWork(StatsAggregationWorker.WORK_NAME)
        Log.i(TAG, "Stats aggregation cancelled")
    }
}
