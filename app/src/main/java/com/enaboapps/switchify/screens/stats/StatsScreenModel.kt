package com.enaboapps.switchify.screens.stats

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.stats.StatsRepository
import com.enaboapps.switchify.service.stats.models.DailyActivity
import com.enaboapps.switchify.service.stats.models.MenuInteractionStats
import com.enaboapps.switchify.service.stats.models.SwitchPressStats
import com.enaboapps.switchify.service.stats.models.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Stats screen.
 * Manages loading and displaying statistics data.
 */
class StatsScreenModel(application: Application) : AndroidViewModel(application) {
    private val statsRepository = StatsRepository(application)

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "StatsScreenModel"
    }

    /**
     * Loads stats for the given time range.
     * Database queries run in parallel on IO dispatcher to avoid blocking the main thread.
     */
    fun loadStats(timeRange: TimeRange) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val (switchStats, menuStats, activityData) = withContext(Dispatchers.IO) {
                    // Run all three repository calls in parallel for better performance
                    val switchStatsDeferred = async { statsRepository.getSwitchPressStats(timeRange) }
                    val menuStatsDeferred = async { statsRepository.getMenuInteractionStats(timeRange) }
                    val activityDataDeferred = async { statsRepository.getActivityData(timeRange) }

                    Triple(
                        switchStatsDeferred.await(),
                        menuStatsDeferred.await(),
                        activityDataDeferred.await()
                    )
                }

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
                    error = getApplication<Application>().getString(R.string.stats_error_generic)
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
     * Formats gesture IDs into human-readable names using string resources.
     */
    private fun formatGestureName(gestureId: String): String {
        val context = getApplication<Application>()
        return when (gestureId) {
            "smile" -> context.getString(R.string.gesture_smile)
            "left_wink" -> context.getString(R.string.gesture_left_wink)
            "right_wink" -> context.getString(R.string.gesture_right_wink)
            "blink" -> context.getString(R.string.gesture_blink)
            "pucker" -> context.getString(R.string.gesture_pucker)
            "head_turn_left" -> context.getString(R.string.gesture_head_left)
            "head_turn_right" -> context.getString(R.string.gesture_head_right)
            "head_turn_up" -> context.getString(R.string.gesture_head_up)
            "head_turn_down" -> context.getString(R.string.gesture_head_down)
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
