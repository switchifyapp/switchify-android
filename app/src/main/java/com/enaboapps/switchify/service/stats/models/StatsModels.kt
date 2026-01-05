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

/**
 * Breakdown item with formatted display values.
 * Used for displaying statistics with locale-aware formatting and percentages.
 */
data class BreakdownItem(
    val label: String,
    val count: Int,
    val formattedCount: String,
    val percentage: String? = null
) {
    /**
     * Returns the display value with optional percentage.
     * Examples: "1,234" or "1,234 (45%)"
     */
    val displayValue: String
        get() = if (percentage != null) {
            "$formattedCount ($percentage)"
        } else {
            formattedCount
        }
}
