package com.enaboapps.switchify.service.stats.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an individual stats event.
 * Stores detailed event data with timestamp for later aggregation.
 */
@Entity(
    tableName = "stats_events",
    indices = [
        Index(value = ["event_type", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class StatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "event_type")
    val eventType: String,  // "switch_press", "menu_open"

    @ColumnInfo(name = "event_subtype")
    val eventSubtype: String?,  // For switches: "external_66", "camera_smile"
                                 // For menus: "main_menu", "device_menu", etc.

    @ColumnInfo(name = "timestamp")
    val timestamp: Long  // Unix timestamp in milliseconds
)
