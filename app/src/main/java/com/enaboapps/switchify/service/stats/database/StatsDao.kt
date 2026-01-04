package com.enaboapps.switchify.service.stats.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Data Access Object for stats database operations.
 * Provides queries for individual events with efficient date-based filtering.
 */
@Dao
interface StatsDao {
    // ==================== Event Write Operations ====================

    @Insert
    suspend fun insertEvent(event: StatsEntity)

    @Insert
    suspend fun insertEvents(events: List<StatsEntity>)

    // ==================== Event Query Operations ====================

    @Query("SELECT COUNT(*) FROM stats_events")
    suspend fun getEventCount(): Int

    @Query("SELECT COUNT(*) FROM stats_events WHERE event_type = :eventType AND event_date >= :startDate AND event_date <= :endDate")
    suspend fun countEventsByType(eventType: String, startDate: String, endDate: String): Int

    @Query("""
        SELECT event_subtype, COUNT(*) as count
        FROM stats_events
        WHERE event_type = :eventType AND event_date >= :startDate AND event_date <= :endDate
        GROUP BY event_subtype
    """)
    suspend fun getEventCountsBySubtype(
        eventType: String,
        startDate: String,
        endDate: String
    ): List<SubtypeCount>

    @Query("""
        SELECT event_date, event_type, COUNT(*) as count
        FROM stats_events
        WHERE event_date >= :startDate AND event_date <= :endDate
        GROUP BY event_date, event_type
        ORDER BY event_date ASC
    """)
    suspend fun getDailyCounts(startDate: String, endDate: String): List<DailyCount>

    // ==================== Event Delete Operations ====================

    @Query("DELETE FROM stats_events")
    suspend fun deleteAllEvents(): Int
}

/**
 * Data class for subtype count query results.
 */
data class SubtypeCount(
    @androidx.room.ColumnInfo(name = "event_subtype") val eventSubtype: String?,
    @androidx.room.ColumnInfo(name = "count") val count: Int
)

/**
 * Data class for daily count query results.
 */
data class DailyCount(
    @androidx.room.ColumnInfo(name = "event_date") val eventDate: String,
    @androidx.room.ColumnInfo(name = "event_type") val eventType: String,
    @androidx.room.ColumnInfo(name = "count") val count: Int
)
