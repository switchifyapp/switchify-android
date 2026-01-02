package com.enaboapps.switchify.service.stats.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for stats database operations.
 * Provides queries for both individual events and aggregated stats.
 */
@Dao
interface StatsDao {
    // ==================== Individual Event Operations ====================

    @Insert
    suspend fun insertEvent(event: StatsEntity)

    @Insert
    suspend fun insertEvents(events: List<StatsEntity>)

    @Query("SELECT * FROM stats_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getEventsInRange(startTime: Long, endTime: Long): List<StatsEntity>

    @Query("SELECT * FROM stats_events WHERE event_type = :eventType AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getEventsByType(
        eventType: String,
        startTime: Long,
        endTime: Long
    ): List<StatsEntity>

    @Query("DELETE FROM stats_events WHERE timestamp < :cutoffTime")
    suspend fun deleteEventsOlderThan(cutoffTime: Long): Int

    @Query("SELECT COUNT(*) FROM stats_events")
    suspend fun getEventCount(): Int

    // ==================== Aggregated Stats Operations ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAggregatedStat(stat: AggregatedStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAggregatedStats(stats: List<AggregatedStatsEntity>)

    @Query("SELECT * FROM aggregated_stats WHERE stat_key = :statKey AND time_bucket >= :startBucket AND time_bucket <= :endBucket")
    suspend fun getAggregatedStats(
        statKey: String,
        startBucket: String,
        endBucket: String
    ): List<AggregatedStatsEntity>

    @Query("SELECT * FROM aggregated_stats WHERE time_bucket >= :startBucket AND time_bucket <= :endBucket ORDER BY time_bucket ASC")
    suspend fun getAllAggregatedStatsInRange(
        startBucket: String,
        endBucket: String
    ): List<AggregatedStatsEntity>

    @Query("SELECT SUM(count) FROM aggregated_stats WHERE stat_key = :statKey AND time_bucket >= :startBucket AND time_bucket <= :endBucket")
    suspend fun getTotalCount(
        statKey: String,
        startBucket: String,
        endBucket: String
    ): Int?

    @Query("SELECT SUM(count) FROM aggregated_stats WHERE stat_key LIKE :statKeyPrefix || '%' AND time_bucket >= :startBucket AND time_bucket <= :endBucket")
    suspend fun getTotalCountByPrefix(
        statKeyPrefix: String,
        startBucket: String,
        endBucket: String
    ): Int?

    @Query("SELECT stat_key, SUM(count) as total FROM aggregated_stats WHERE stat_key LIKE :statKeyPrefix || '%' AND time_bucket >= :startBucket AND time_bucket <= :endBucket GROUP BY stat_key ORDER BY total DESC")
    suspend fun getAggregatedCountsByPrefix(
        statKeyPrefix: String,
        startBucket: String,
        endBucket: String
    ): List<StatKeyCount>

    @Query("DELETE FROM aggregated_stats WHERE time_bucket < :cutoffBucket")
    suspend fun deleteAggregatedStatsOlderThan(cutoffBucket: String): Int

    @Query("DELETE FROM aggregated_stats")
    suspend fun deleteAllAggregatedStats(): Int

    @Query("SELECT COUNT(*) FROM aggregated_stats")
    suspend fun getAggregatedStatsCount(): Int
}

/**
 * Helper data class for aggregated counts by stat key.
 */
data class StatKeyCount(
    val stat_key: String,
    val total: Int
)
