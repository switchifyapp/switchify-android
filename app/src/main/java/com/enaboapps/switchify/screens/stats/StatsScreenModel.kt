package com.enaboapps.switchify.screens.stats

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.stats.StatsRepository
import com.enaboapps.switchify.service.stats.models.BreakdownItem
import com.enaboapps.switchify.service.stats.models.DailyActivity
import com.enaboapps.switchify.service.stats.models.MenuInteractionStats
import com.enaboapps.switchify.service.stats.models.SwitchPressStats
import com.enaboapps.switchify.service.stats.models.TimeRange
import com.enaboapps.switchify.utils.StatsFormatter
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
     * Gets the top N most-used menus with human-readable names and formatted counts.
     */
    fun getTopMenus(n: Int = 5): List<BreakdownItem> {
        val menuCounts = _uiState.value.menuStats?.menuOpenCounts ?: return emptyList()
        val total = menuCounts.values.sum()

        return menuCounts
            .entries
            .sortedByDescending { it.value }
            .take(n)
            .map { (menuId, count) ->
                BreakdownItem(
                    label = formatMenuName(menuId),
                    count = count,
                    formattedCount = StatsFormatter.formatNumber(count),
                    percentage = if (total > 0) StatsFormatter.formatPercentage(count, total) else null
                )
            }
    }

    /**
     * Formats menu IDs into human-readable names using string resources.
     */
    private fun formatMenuName(menuId: String): String {
        val context = getApplication<Application>()
        return when (menuId) {
            MenuConstants.MenuIds.MAIN_MENU -> context.getString(R.string.menu_title_main)
            MenuConstants.MenuIds.DEVICE_MENU -> context.getString(R.string.menu_title_device)
            MenuConstants.MenuIds.VOLUME_CONTROL_MENU -> context.getString(R.string.menu_title_volume_control)
            MenuConstants.MenuIds.GESTURES_MENU -> context.getString(R.string.menu_title_gestures)
            MenuConstants.MenuIds.TAP_GESTURES_MENU -> context.getString(R.string.menu_title_tap)
            MenuConstants.MenuIds.SWIPE_GESTURES_MENU -> context.getString(R.string.menu_title_swipe)
            MenuConstants.MenuIds.PINCH_GESTURES_MENU -> context.getString(R.string.menu_title_pinch)
            MenuConstants.MenuIds.SCROLL_MENU -> context.getString(R.string.menu_title_scroll)
            MenuConstants.MenuIds.MEDIA_CONTROL_MENU -> context.getString(R.string.menu_title_media_control)
            MenuConstants.MenuIds.EDIT_MENU -> context.getString(R.string.menu_title_edit)
            else -> menuId.replace("_", " ").split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
        }
    }

    /**
     * Gets breakdown of external switches with formatted counts and percentages.
     */
    fun getExternalSwitchBreakdown(): List<BreakdownItem> {
        val externalPresses = _uiState.value.switchStats?.externalSwitchPresses ?: return emptyList()
        val total = externalPresses.values.sum()

        return externalPresses
            .entries
            .sortedByDescending { it.value }
            .map { (keyCode, count) ->
                BreakdownItem(
                    label = "Key $keyCode",
                    count = count,
                    formattedCount = StatsFormatter.formatNumber(count),
                    percentage = if (total > 0) StatsFormatter.formatPercentage(count, total) else null
                )
            }
    }

    /**
     * Gets breakdown of camera gestures with formatted counts and percentages.
     */
    fun getCameraGestureBreakdown(): List<BreakdownItem> {
        val cameraPresses = _uiState.value.switchStats?.cameraSwitchPresses ?: return emptyList()
        val total = cameraPresses.values.sum()

        return cameraPresses
            .entries
            .sortedByDescending { it.value }
            .map { (gestureId, count) ->
                BreakdownItem(
                    label = formatGestureName(gestureId),
                    count = count,
                    formattedCount = StatsFormatter.formatNumber(count),
                    percentage = if (total > 0) StatsFormatter.formatPercentage(count, total) else null
                )
            }
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

    /**
     * Clears all statistics data.
     */
    fun clearAllStats(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    statsRepository.clearAllStats()
                }
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing stats", e)
                onError(getApplication<Application>().getString(R.string.stats_clear_data_error))
            }
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
