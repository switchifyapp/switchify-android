package com.enaboapps.switchify.service.stats

import android.content.Context
import com.enaboapps.switchify.service.stats.database.AggregatedStatsEntity
import com.enaboapps.switchify.service.stats.database.StatKeyCount
import com.enaboapps.switchify.service.stats.database.StatsDatabase
import com.enaboapps.switchify.service.stats.database.StatsEntity
import com.enaboapps.switchify.service.stats.models.DailyActivity
import com.enaboapps.switchify.service.stats.models.MenuInteractionStats
import com.enaboapps.switchify.service.stats.models.SwitchPressStats
import com.enaboapps.switchify.service.stats.models.TimeRange
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Repository for managing stats data.
 * Provides API for recording events and querying statistics.
 */
class StatsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = StatsDatabase.getInstance(context)
    private val dao = database.statsDao()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ==================== Recording Methods (Non-blocking) ====================

    /**
     * Records a switch press event.
     * Non-blocking - launches coroutine for database write.
     */
    fun recordSwitchPress(switchType: String, switchCode: String) {
        val event = StatsEntity(
            eventType = "switch_press",
            eventSubtype = "${switchType}_$switchCode",
            timestamp = System.currentTimeMillis()
        )
        coroutineScope.launch {
            dao.insertEvent(event)
        }
    }

    /**
     * Records a menu open event.
     * Non-blocking - launches coroutine for database write.
     */
    fun recordMenuOpen(menuId: String, fromMenuId: String? = null) {
        val event = StatsEntity(
            eventType = "menu_open",
            eventSubtype = menuId,
            timestamp = System.currentTimeMillis()
        )
        coroutineScope.launch {
            dao.insertEvent(event)
        }
    }

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

        // Get all switch press aggregated stats
        val externalStats = dao.getAggregatedCountsByPrefix(
            "switch_external_",
            startBucket,
            endBucket
        )

        val cameraStats = dao.getAggregatedCountsByPrefix(
            "switch_camera_",
            startBucket,
            endBucket
        )

        // Build breakdown maps
        val externalMap = externalStats.associate {
            it.stat_key.removePrefix("switch_external_") to it.total
        }
        val cameraMap = cameraStats.associate {
            it.stat_key.removePrefix("switch_camera_") to it.total
        }

        // Get daily totals
        val pressesPerDay = getDailyTotals("switch_", startBucket, endBucket)

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
        val endBucket = endDate.toString()

        // Get all menu open aggregated stats
        val menuStats = dao.getAggregatedCountsByPrefix(
            "menu_",
            startBucket,
            endBucket
        )

        // Build menu counts map
        val menuCounts = menuStats.associate {
            it.stat_key.removePrefix("menu_") to it.total
        }

        // Get daily totals
        val opensPerDay = getDailyTotals("menu_", startBucket, endBucket)

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

        val allStats = dao.getAllAggregatedStatsInRange(startBucket, endBucket)

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
     * Triggers aggregation for all unaggregated days.
     */
    suspend fun triggerAggregation() {
        // Aggregate yesterday (if not done yet)
        val yesterday = LocalDate.now().minusDays(1)
        aggregateDay(yesterday)

        // Aggregate today (for real-time stats)
        val today = LocalDate.now()
        aggregateDay(today)
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
        val allStats = dao.getAllAggregatedStatsInRange(startBucket, endBucket)

        return allStats
            .filter { it.statKey.startsWith(prefix) }
            .groupBy { it.timeBucket }
            .mapValues { (_, stats) -> stats.sumOf { it.count } }
    }
}
