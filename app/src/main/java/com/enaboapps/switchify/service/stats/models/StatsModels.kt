package com.enaboapps.switchify.service.stats.models

/**
 * Time range for stats queries.
 */
enum class TimeRange {
    TODAY,
    WEEK,
    MONTH,
    ALL_TIME
}

/**
 * Statistics for switch presses.
 */
data class SwitchPressStats(
    val totalPresses: Int,
    val externalSwitchPresses: Map<String, Int>,  // keyCode -> count
    val cameraSwitchPresses: Map<String, Int>,     // gesture -> count
    val pressesPerDay: Map<String, Int>            // date (YYYY-MM-DD) -> count
)

/**
 * Statistics for menu interactions.
 */
data class MenuInteractionStats(
    val totalMenuOpens: Int,
    val menuOpenCounts: Map<String, Int>,          // menuId -> count
    val opensPerDay: Map<String, Int>              // date (YYYY-MM-DD) -> count
)

/**
 * Daily activity data for charts.
 */
data class DailyActivity(
    val date: String,  // YYYY-MM-DD
    val switchPresses: Int,
    val menuOpens: Int
)
