package com.enaboapps.switchify.screens.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.stats.components.ActivityChart
import com.enaboapps.switchify.screens.stats.components.BreakdownList
import com.enaboapps.switchify.screens.stats.components.StatCard
import com.enaboapps.switchify.service.stats.models.TimeRange

/**
 * Stats screen displaying usage statistics.
 */
@Composable
fun StatsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: StatsScreenModel = viewModel { StatsScreenModel(context) }
    val uiState by viewModel.uiState.collectAsState()

    var selectedTimeRange by remember { mutableStateOf(TimeRange.WEEK) }

    // Load stats when time range changes
    LaunchedEffect(selectedTimeRange) {
        viewModel.loadStats(selectedTimeRange)
    }

    BaseView(
        titleResId = R.string.screen_title_stats,
        navController = navController,
        enableScroll = false
    ) {
        ScrollableView {
            // Time range selector
            TimeRangeSelector(
                selectedRange = selectedTimeRange,
                onRangeSelected = { selectedTimeRange = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Show loading indicator
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.stats_loading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Show error if any
            uiState.error?.let { error ->
                Text(
                    text = stringResource(R.string.stats_error, error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // Show stats when loaded
            if (!uiState.isLoading && uiState.error == null) {
                // Switch press stats
                Section(titleResId = R.string.stats_section_switch_presses) {
                    val switchStats = uiState.switchStats
                    if (switchStats != null) {
                        StatCard(
                            label = stringResource(R.string.stats_total_presses),
                            value = switchStats.totalPresses.toString()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatCard(
                                label = stringResource(R.string.stats_external_switches),
                                value = switchStats.externalSwitchPresses.values.sum().toString(),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatCard(
                                label = stringResource(R.string.stats_camera_gestures),
                                value = switchStats.cameraSwitchPresses.values.sum().toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // External switch breakdown
                        if (switchStats.externalSwitchPresses.isNotEmpty()) {
                            BreakdownList(
                                title = stringResource(R.string.stats_external_breakdown),
                                items = viewModel.getExternalSwitchBreakdown()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Camera gesture breakdown
                        if (switchStats.cameraSwitchPresses.isNotEmpty()) {
                            BreakdownList(
                                title = stringResource(R.string.stats_camera_breakdown),
                                items = viewModel.getCameraGestureBreakdown()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Menu interaction stats
                Section(titleResId = R.string.stats_section_menu_interactions) {
                    val menuStats = uiState.menuStats
                    if (menuStats != null) {
                        StatCard(
                            label = stringResource(R.string.stats_total_menu_opens),
                            value = menuStats.totalMenuOpens.toString()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Top menus
                        val topMenus = viewModel.getTopMenus(5)
                        if (topMenus.isNotEmpty()) {
                            BreakdownList(
                                title = stringResource(R.string.stats_top_menus),
                                items = topMenus
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Activity over time chart
                Section(titleResId = R.string.stats_section_activity) {
                    uiState.activityData?.let { data ->
                        ActivityChart(
                            data = data,
                            title = stringResource(R.string.stats_activity_over_time)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
    ) {
        TimeRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = {
                    Text(
                        text = when (range) {
                            TimeRange.TODAY -> stringResource(R.string.stats_time_today)
                            TimeRange.WEEK -> stringResource(R.string.stats_time_week)
                            TimeRange.MONTH -> stringResource(R.string.stats_time_month)
                            TimeRange.ALL_TIME -> stringResource(R.string.stats_time_all)
                        }
                    )
                }
            )
        }
    }
}
