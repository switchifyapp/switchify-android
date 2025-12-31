package com.enaboapps.switchify.screens.stats

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.service.stats.StatsRepository
import com.enaboapps.switchify.service.stats.models.DailyActivity
import com.enaboapps.switchify.service.stats.models.MenuInteractionStats
import com.enaboapps.switchify.service.stats.models.SwitchPressStats
import com.enaboapps.switchify.service.stats.models.TimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Stats screen.
 * Manages loading and displaying statistics data.
 */
class StatsScreenModel(context: Context) : ViewModel() {
    private val statsRepository = StatsRepository(context)

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "StatsScreenModel"
    }

    /**
     * Loads stats for the given time range.
     */
    fun loadStats(timeRange: TimeRange) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val switchStats = statsRepository.getSwitchPressStats(timeRange)
                val menuStats = statsRepository.getMenuInteractionStats(timeRange)
                val activityData = statsRepository.getActivityData(timeRange)

                _uiState.value = StatsUiState(
                    switchStats = switchStats,
                    menuStats = menuStats,
                    activityData = activityData,
                    isLoading = false,
                    error = null
                )

                Log.d(TAG, "Stats loaded for $timeRange: ${switchStats.totalPresses} presses, ${menuStats.totalMenuOpens} menu opens")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stats", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Gets the top N most-used menus.
     */
    fun getTopMenus(n: Int = 5): List<Pair<String, Int>> {
        return _uiState.value.menuStats?.menuOpenCounts
            ?.entries
            ?.sortedByDescending { it.value }
            ?.take(n)
            ?.map { it.key to it.value }
            ?: emptyList()
    }

    /**
     * Gets breakdown of external switches.
     */
    fun getExternalSwitchBreakdown(): List<Pair<String, Int>> {
        return _uiState.value.switchStats?.externalSwitchPresses
            ?.entries
            ?.sortedByDescending { it.value }
            ?.map { "Key ${it.key}" to it.value }
            ?: emptyList()
    }

    /**
     * Gets breakdown of camera gestures.
     */
    fun getCameraGestureBreakdown(): List<Pair<String, Int>> {
        return _uiState.value.switchStats?.cameraSwitchPresses
            ?.entries
            ?.sortedByDescending { it.value }
            ?.map { formatGestureName(it.key) to it.value }
            ?: emptyList()
    }

    /**
     * Formats gesture IDs into human-readable names.
     */
    private fun formatGestureName(gestureId: String): String {
        return when (gestureId) {
            "smile" -> "Smile"
            "left_wink" -> "Left Wink"
            "right_wink" -> "Right Wink"
            "blink" -> "Blink"
            "pucker" -> "Pucker"
            "head_turn_left" -> "Head Left"
            "head_turn_right" -> "Head Right"
            "head_turn_up" -> "Head Up"
            "head_turn_down" -> "Head Down"
            else -> gestureId.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * UI state for the Stats screen.
 */
data class StatsUiState(
    val switchStats: SwitchPressStats? = null,
    val menuStats: MenuInteractionStats? = null,
    val activityData: List<DailyActivity>? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)
