package com.enaboapps.switchify.service.stats

import android.content.Context
import android.content.SharedPreferences
import com.enaboapps.switchify.service.stats.database.AggregatedStatsEntity
import com.enaboapps.switchify.service.stats.database.StatKeyCount
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
        // Prevent database operations when device is locked
        if (!DeviceLockObserver.isUserUnlocked(appContext)) {
            android.util.Log.w("StatsRepository", "Device is locked, skipping batch insert of ${events.size} events")
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
        val startBucket = startDate.toString()
        val endBucket = endDate.toString()

        // Exclude today from aggregated stats to avoid double-counting
        // (today's events might have been aggregated already)
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val aggregatedEndBucket = if (endDate.isAfter(yesterday)) yesterday.toString() else endBucket

        // Get aggregated stats up to yesterday only
        val externalStats = if (startBucket <= aggregatedEndBucket) {
            dao.getAggregatedCountsByPrefix(
                "switch_external_",
                startBucket,
                aggregatedEndBucket
            )
        } else {
            emptyList()
        }

        val cameraStats = if (startBucket <= aggregatedEndBucket) {
            dao.getAggregatedCountsByPrefix(
                "switch_camera_",
                startBucket,
                aggregatedEndBucket
            )
        } else {
            emptyList()
        }

        // Build breakdown maps from aggregated data
        val externalMap = externalStats.associate {
            it.stat_key.removePrefix("switch_external_") to it.total
        }.toMutableMap()
        val cameraMap = cameraStats.associate {
            it.stat_key.removePrefix("switch_camera_") to it.total
        }.toMutableMap()

        // Add today's raw unaggregated events for real-time updates
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val todayEvents = dao.getEventsByType("switch_press", todayStart, todayEnd)
        todayEvents.forEach { event ->
            val subtype = event.eventSubtype ?: return@forEach
            when {
                subtype.startsWith("external_") -> {
                    val key = subtype.removePrefix("external_")
                    externalMap[key] = (externalMap[key] ?: 0) + 1
                }
                subtype.startsWith("camera_") -> {
                    val key = subtype.removePrefix("camera_")
                    cameraMap[key] = (cameraMap[key] ?: 0) + 1
                }
            }
        }

        // Get daily totals (excluding today from aggregated stats)
        val pressesPerDay = getDailyTotals("switch_", startBucket, aggregatedEndBucket).toMutableMap()

        // Add today's total to pressesPerDay
        val todayTotal = todayEvents.size
        if (todayTotal > 0) {
            val todayDateStr = today.toString()
            pressesPerDay[todayDateStr] = (pressesPerDay[todayDateStr] ?: 0) + todayTotal
        }

        val totalPresses = (externalMap.values.sum() + cameraMap.values.sum())

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
        val startBucket = startDate.toString()

        // Exclude today from aggregated stats to avoid double-counting
        // (today's events might have been aggregated already)
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val aggregatedEndBucket = if (endDate.isAfter(yesterday)) yesterday.toString() else endDate.toString()

        // Get aggregated menu stats up to yesterday only
        val menuStats = if (startBucket <= aggregatedEndBucket) {
            dao.getAggregatedCountsByPrefix(
                "menu_",
                startBucket,
                aggregatedEndBucket
            )
        } else {
            emptyList()
        }

        // Build menu counts map from aggregated data
        val menuCounts = menuStats.associate {
            it.stat_key.removePrefix("menu_") to it.total
        }.toMutableMap()

        // Add today's raw unaggregated events for real-time updates
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val todayEvents = dao.getEventsByType("menu_open", todayStart, todayEnd)
        todayEvents.forEach { event ->
            val menuId = event.eventSubtype ?: return@forEach
            menuCounts[menuId] = (menuCounts[menuId] ?: 0) + 1
        }

        // Get daily totals (excluding today from aggregated stats)
        val opensPerDay = getDailyTotals("menu_", startBucket, aggregatedEndBucket).toMutableMap()

        // Add today's total to opensPerDay
        val todayTotal = todayEvents.size
        if (todayTotal > 0) {
            val todayDateStr = today.toString()
            opensPerDay[todayDateStr] = (opensPerDay[todayDateStr] ?: 0) + todayTotal
        }

        val totalOpens = menuCounts.values.sum()

        return MenuInteractionStats(
            totalMenuOpens = totalOpens,
            menuOpenCounts = menuCounts,
            opensPerDay = opensPerDay
        )
    }

    /**
     * Gets daily activity data for charts.
     */
    suspend fun getActivityData(timeRange: TimeRange): List<DailyActivity> {
        val (startDate, endDate) = getDateRange(timeRange)
        val startBucket = startDate.toString()
        val endBucket = endDate.toString()

        // Exclude today from aggregated stats to avoid double-counting
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val aggregatedEndBucket = if (endDate.isAfter(yesterday)) yesterday.toString() else endBucket

        val allStats = if (startBucket <= aggregatedEndBucket) {
            dao.getAllAggregatedStatsInRange(startBucket, aggregatedEndBucket)
        } else {
            emptyList()
        }

        // Group by date
        val dateMap = mutableMapOf<String, Pair<Int, Int>>()  // date -> (switches, menus)

        allStats.forEach { stat ->
            val currentPair = dateMap.getOrDefault(stat.timeBucket, 0 to 0)
            when {
                stat.statKey.startsWith("switch_") -> {
                    dateMap[stat.timeBucket] = (currentPair.first + stat.count) to currentPair.second
                }
                stat.statKey.startsWith("menu_") -> {
                    dateMap[stat.timeBucket] = currentPair.first to (currentPair.second + stat.count)
                }
            }
        }

        // Add today's raw unaggregated events for real-time updates
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val todayEvents = dao.getEventsInRange(todayStart, todayEnd)
        val todayDateStr = today.toString()
        var todaySwitches = 0
        var todayMenus = 0

        todayEvents.forEach { event ->
            when (event.eventType) {
                "switch_press" -> todaySwitches++
                "menu_open" -> todayMenus++
            }
        }

        if (todaySwitches > 0 || todayMenus > 0) {
            val currentPair = dateMap.getOrDefault(todayDateStr, 0 to 0)
            dateMap[todayDateStr] = (currentPair.first + todaySwitches) to (currentPair.second + todayMenus)
        }

        // Fill in missing dates with zeros
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

    // ==================== Aggregation Methods ====================

    /**
     * Aggregates events for a specific day into aggregated stats.
     */
    suspend fun aggregateDay(date: LocalDate) {
        // Prevent database operations when device is locked
        if (!DeviceLockObserver.isUserUnlocked(appContext)) {
            android.util.Log.w("StatsRepository", "Device is locked, skipping aggregation for $date")
            return
        }

        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

        val events = dao.getEventsInRange(startOfDay, endOfDay)

        // Group events by (eventType, eventSubtype)
        val grouped = events.groupBy { "${it.eventType}_${it.eventSubtype ?: "unknown"}" }

        // Create aggregated stats
        val aggregatedStats = grouped.map { (key, eventList) ->
            val statKey = key.replace("switch_press_", "switch_")
                              .replace("menu_open_", "menu_")
            AggregatedStatsEntity(
                id = "${statKey}_${date}",
                statKey = statKey,
                timeBucket = date.toString(),
                count = eventList.size,
                lastUpdated = System.currentTimeMillis()
            )
        }

        if (aggregatedStats.isNotEmpty()) {
            dao.insertAggregatedStats(aggregatedStats)
        }
    }

    /**
     * Triggers aggregation for completed days.
     * Only aggregates yesterday and older - never today since it's still accumulating events.
     */
    suspend fun triggerAggregation() {
        // Aggregate yesterday (if not done yet)
        val yesterday = LocalDate.now().minusDays(1)
        aggregateDay(yesterday)

        // Don't aggregate today - it's still accumulating events
        // Query methods will read today's raw events directly for real-time stats

        // Check for milestones after aggregation
        checkMilestones()
    }

    /**
     * Checks for milestone achievements and logs them.
     * Milestones are only logged once.
     */
    private suspend fun checkMilestones() {
        try {
            // Get total switch presses across all time
            val stats = getSwitchPressStats(TimeRange.ALL_TIME)
            val totalPresses = stats.totalPresses

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
        }
    }

    // ==================== Data Maintenance Methods ====================

    /**
     * Cleans up old events beyond the retention period.
     */
    suspend fun cleanupOldEvents(retentionDays: Int = 90) {
        val cutoffTime = Instant.now()
            .minus(retentionDays.toLong(), ChronoUnit.DAYS)
            .toEpochMilli()

        val deleted = dao.deleteEventsOlderThan(cutoffTime)
        if (deleted > 0) {
            android.util.Log.i("StatsRepository", "Cleaned up $deleted old events")
        }
    }

    /**
     * Gets total event count (for debugging).
     */
    suspend fun getEventCount(): Int {
        return dao.getEventCount()
    }

    /**
     * Gets aggregated stats count (for debugging).
     */
    suspend fun getAggregatedStatsCount(): Int {
        return dao.getAggregatedStatsCount()
    }

    /**
     * Clears all statistics data (both events and aggregated stats).
     * Also resets milestone tracking.
     */
    suspend fun clearAllStats() {
        // Prevent database operations when device is locked
        if (!DeviceLockObserver.isUserUnlocked(appContext)) {
            android.util.Log.w("StatsRepository", "Device is locked, skipping clear stats")
            throw IllegalStateException("Cannot clear stats while device is locked")
        }

        try {
            // Clear all individual events
            val eventsDeleted = dao.deleteEventsOlderThan(System.currentTimeMillis() + 1)

            // Clear all aggregated stats
            val aggregatedDeleted = dao.deleteAllAggregatedStats()

            // Reset milestone tracking
            preferences.edit()
                .putBoolean(MILESTONE_100_REACHED, false)
                .putBoolean(MILESTONE_1000_REACHED, false)
                .apply()

            android.util.Log.i(
                "StatsRepository",
                "All stats data cleared successfully: $eventsDeleted events, $aggregatedDeleted aggregated stats"
            )
        } catch (e: Exception) {
            android.util.Log.e("StatsRepository", "Error clearing stats data", e)
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

    private suspend fun getDailyTotals(prefix: String, startBucket: String, endBucket: String): Map<String, Int> {
        val allStats = if (startBucket <= endBucket) {
            dao.getAllAggregatedStatsInRange(startBucket, endBucket)
        } else {
            emptyList()
        }

        return allStats
            .filter { it.statKey.startsWith(prefix) }
            .groupBy { it.timeBucket }
            .mapValues { (_, stats) -> stats.sumOf { it.count } }
    }
}
