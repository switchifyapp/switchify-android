package com.enaboapps.switchify.service.stats

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for aggregating stats data.
 * Runs daily to aggregate events into daily summaries and clean up old data.
 */
class StatsAggregationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "StatsAggregationWorker"
        const val WORK_NAME = "stats_aggregation_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting stats aggregation")

            val repository = StatsRepository(applicationContext)

            // Trigger aggregation for yesterday and today
            repository.triggerAggregation()

            // Clean up old events (90 days retention)
            repository.cleanupOldEvents(retentionDays = 90)

            val eventCount = repository.getEventCount()
            val aggregatedCount = repository.getAggregatedStatsCount()

            Log.i(TAG, "Stats aggregation completed. Events: $eventCount, Aggregated: $aggregatedCount")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during stats aggregation", e)
            Result.retry()
        }
    }
}
