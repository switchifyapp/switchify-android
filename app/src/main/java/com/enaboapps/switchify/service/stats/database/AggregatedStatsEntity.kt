package com.enaboapps.switchify.service.stats.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing aggregated stats for a specific time bucket (typically daily).
 * Used for efficient querying and cloud sync.
 */
@Entity(
    tableName = "aggregated_stats",
    indices = [Index(value = ["stat_key", "time_bucket"])]
)
data class AggregatedStatsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,  // Composite: "switch_external_66_2025-01-15"

    @ColumnInfo(name = "stat_key")
    val statKey: String,  // "switch_external_66", "menu_main_menu"

    @ColumnInfo(name = "time_bucket")
    val timeBucket: String,  // "2025-01-15" (YYYY-MM-DD)

    @ColumnInfo(name = "count")
    val count: Int,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long  // Unix timestamp in milliseconds
)
