package com.enaboapps.switchify.service.stats

import android.content.Context
import android.content.SharedPreferences
import com.enaboapps.switchify.service.stats.database.StatsDatabase
import com.enaboapps.switchify.service.stats.database.StatsEntity
import com.enaboapps.switchify.service.stats.models.DailyActivity
import com.enaboapps.switchify.service.stats.models.MenuInteractionStats
import com.enaboapps.switchify.service.stats.models.SwitchPressStats
import com.enaboapps.switchify.service.stats.models.TimeRange
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Repository for managing stats data.
 * Provides API for querying statistics.
 *
 * NOTE: Use StatsCollector for recording events, not this repository directly.
 * StatsCollector provides batched writes for better performance.
 */
class StatsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = StatsDatabase.getInstance(context)
    private val dao = database.statsDao()
    private val preferences: SharedPreferences = appContext.getSharedPreferences("stats_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val MILESTONE_100_REACHED = "stats_milestone_100_reached"
        private const val MILESTONE_1000_REACHED = "stats_milestone_1000_reached"
    }

    // ==================== Recording Methods (Internal - used by StatsCollector) ====================

    /**
     * Batch insert multiple events.
     * Used by StatsCollector for efficient batched writes.
     */
    suspend fun batchInsertEvents(events: List<StatsEntity>) {
        if (!DeviceLockObserver.isUserUnlocked(appContext)) {
            android.util.Log.w("StatsRepository", "Device is locked, skipping batch insert of ${events.size} events")
            Logger.log(
                LogEvent.StatsFlushSkipped,
                data = mapOf(
                    "result" to "skipped",
                    "reason" to "device_locked_batch_insert",
                    "queued_count" to events.size
                )
            )
            return
        }

        if (events.isNotEmpty()) {
            dao.insertEvents(events)
        }
    }

    // ==================== Query Methods (Suspend) ====================

    /**
     * Gets switch press statistics for a given time range.
     */
    suspend fun getSwitchPressStats(timeRange: TimeRange): SwitchPressStats {
        val (startDate, endDate) = getDateRange(timeRange)
        val startDateStr = startDate.toString()
        val endDateStr = endDate.toString()

        // Get all switch press events in range
        val subtypeCounts = dao.getEventCountsBySubtype("switch_press", startDateStr, endDateStr)

        // Split into external vs camera
        val externalMap = mutableMapOf<String, Int>()
        val cameraMap = mutableMapOf<String, Int>()

        subtypeCounts.forEach { (subtype, count) ->
            when {
                subtype?.startsWith("external_") == true -> {
                    val key = subtype.removePrefix("external_")
                    externalMap[key] = count
                }
                subtype?.startsWith("camera_") == true -> {
                    val key = subtype.removePrefix("camera_")
                    cameraMap[key] = count
                }
            }
        }

        // Get daily breakdown
        val dailyCounts = dao.getDailyCounts(startDateStr, endDateStr)
        val pressesPerDay = dailyCounts
            .filter { it.eventType == "switch_press" }
            .associate { it.eventDate to it.count }

        val totalPresses = externalMap.values.sum() + cameraMap.values.sum()

        return SwitchPressStats(
            totalPresses = totalPresses,
            externalSwitchPresses = externalMap,
            cameraSwitchPresses = cameraMap,
            pressesPerDay = pressesPerDay
        )
    }

    /**
     * Gets menu interaction statistics for a given time range.
     */
    suspend fun getMenuInteractionStats(timeRange: TimeRange): MenuInteractionStats {
        val (startDate, endDate) = getDateRange(timeRange)
        val startDateStr = startDate.toString()
        val endDateStr = endDate.toString()

        val subtypeCounts = dao.getEventCountsBySubtype("menu_open", startDateStr, endDateStr)
        val menuCounts = subtypeCounts.associate { (subtype, count) ->
            (subtype ?: "unknown") to count
        }

        val dailyCounts = dao.getDailyCounts(startDateStr, endDateStr)
        val opensPerDay = dailyCounts
            .filter { it.eventType == "menu_open" }
            .associate { it.eventDate to it.count }

        return MenuInteractionStats(
            totalMenuOpens = menuCounts.values.sum(),
            menuOpenCounts = menuCounts,
            opensPerDay = opensPerDay
        )
    }

    /**
     * Gets daily activity data for charts.
     */
    suspend fun getActivityData(timeRange: TimeRange): List<DailyActivity> {
        val (startDate, endDate) = getDateRange(timeRange)
        val startDateStr = startDate.toString()
        val endDateStr = endDate.toString()

        val dailyCounts = dao.getDailyCounts(startDateStr, endDateStr)

        // Group by date
        val dateMap = dailyCounts.groupBy { it.eventDate }
            .mapValues { (_, counts) ->
                val switches = counts.filter { it.eventType == "switch_press" }.sumOf { it.count }
                val menus = counts.filter { it.eventType == "menu_open" }.sumOf { it.count }
                switches to menus
            }

        // Fill missing dates with zeros
        val result = mutableListOf<DailyActivity>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val dateStr = currentDate.toString()
            val (switches, menus) = dateMap.getOrDefault(dateStr, 0 to 0)
            result.add(DailyActivity(dateStr, switches, menus))
            currentDate = currentDate.plusDays(1)
        }

        return result
    }

    // ==================== Milestone Checking ====================

    /**
     * Checks for milestone achievements and logs them.
     * Milestones are only logged once.
     * Called periodically by StatsCollector after flushing events.
     */
    suspend fun checkMilestones() {
        try {
            // Simple count query (very fast with index)
            val totalPresses = dao.countEventsByType(
                "switch_press",
                "2020-01-01",
                LocalDate.now().toString()
            )

            // Check for 100 presses milestone
            if (totalPresses >= 100 && !preferences.getBoolean(MILESTONE_100_REACHED, false)) {
                Logger.log(LogEvent.Milestone100SwitchPresses)
                preferences.edit().putBoolean(MILESTONE_100_REACHED, true).apply()
                android.util.Log.i("StatsRepository", "Milestone reached: 100 switch presses")
            }

            // Check for 1000 presses milestone
            if (totalPresses >= 1000 && !preferences.getBoolean(MILESTONE_1000_REACHED, false)) {
                Logger.log(LogEvent.Milestone1000SwitchPresses)
                preferences.edit().putBoolean(MILESTONE_1000_REACHED, true).apply()
                android.util.Log.i("StatsRepository", "Milestone reached: 1000 switch presses")
            }
        } catch (e: Exception) {
            android.util.Log.e("StatsRepository", "Error checking milestones", e)
            Logger.log(
                LogEvent.StatsFlushFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "milestone_check_failed"
                ),
                throwable = e
            )
        }
    }

    // ==================== Data Maintenance Methods ====================

    /**
     * Gets total event count (for debugging).
     */
    suspend fun getEventCount(): Int {
        return dao.getEventCount()
    }

    /**
     * Clears all statistics data.
     * Also resets milestone tracking.
     */
    suspend fun clearAllStats() {
        if (!DeviceLockObserver.isUserUnlocked(appContext)) {
            android.util.Log.w("StatsRepository", "Device is locked, skipping clear stats")
            Logger.log(
                LogEvent.StatsFlushSkipped,
                data = mapOf(
                    "result" to "skipped",
                    "reason" to "device_locked_clear_stats"
                )
            )
            throw IllegalStateException("Cannot clear stats while device is locked")
        }

        try {
            // Clear all events
            val eventsDeleted = dao.deleteAllEvents()

            // Reset milestone tracking
            preferences.edit()
                .putBoolean(MILESTONE_100_REACHED, false)
                .putBoolean(MILESTONE_1000_REACHED, false)
                .apply()

            android.util.Log.i(
                "StatsRepository",
                "All stats data cleared successfully: $eventsDeleted events"
            )
        } catch (e: Exception) {
            android.util.Log.e("StatsRepository", "Error clearing stats data", e)
            Logger.log(
                LogEvent.StatsFlushFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "clear_stats_failed"
                ),
                throwable = e
            )
            throw e
        }
    }

    // ==================== Helper Methods ====================

    private fun getDateRange(timeRange: TimeRange): Pair<LocalDate, LocalDate> {
        val endDate = LocalDate.now()
        val startDate = when (timeRange) {
            TimeRange.TODAY -> endDate
            TimeRange.WEEK -> endDate.minusDays(6)
            TimeRange.MONTH -> endDate.minusDays(29)
            TimeRange.ALL_TIME -> LocalDate.of(2020, 1, 1)  // Arbitrary old date
        }
        return startDate to endDate
    }
}
