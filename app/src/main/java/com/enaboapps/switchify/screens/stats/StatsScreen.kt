package com.enaboapps.switchify.screens.stats

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger

/**
 * Stats screen displaying usage statistics.
 */
@Composable
fun StatsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: StatsScreenModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return StatsScreenModel(context.applicationContext as android.app.Application) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTimeRange by remember { mutableStateOf(TimeRange.WEEK) }
    var showClearDialog by remember { mutableStateOf(false) }
    var autoRefreshJob by remember { mutableStateOf<Job?>(null) }

    // Log when stats screen is opened
    LaunchedEffect(Unit) {
        Logger.log(LogEvent.StatsScreenOpened)
    }

    // Load stats when time range changes or when screen appears (via navigation)
    LaunchedEffect(selectedTimeRange, navController.currentBackStackEntry) {
        viewModel.loadStats(selectedTimeRange)
    }

    // Lifecycle-aware auto-refresh that stops when screen is not visible
    DisposableEffect(lifecycleOwner, selectedTimeRange) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Start auto-refresh when screen becomes visible
                    autoRefreshJob?.cancel()
                    autoRefreshJob = coroutineScope.launch {
                        while (isActive) {
                            delay(6000) // Wait 6 seconds (just after the 5-second flush)
                            if (isActive) {
                                viewModel.loadStats(selectedTimeRange)
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Stop auto-refresh when screen is no longer visible
                    autoRefreshJob?.cancel()
                    autoRefreshJob = null
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            // Clean up observer and cancel refresh job when composable leaves composition
            lifecycleOwner.lifecycle.removeObserver(observer)
            autoRefreshJob?.cancel()
            autoRefreshJob = null
        }
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
                onRangeSelected = { range ->
                    selectedTimeRange = range
                    Logger.log(LogEvent.StatsTimeRangeChanged)
                }
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

                Spacer(modifier = Modifier.height(24.dp))

                // Clear data button
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.stats_clear_data))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Clear data confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.stats_clear_data)) },
            text = { Text(stringResource(R.string.stats_clear_data_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllStats(
                            onSuccess = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.stats_clear_data_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Reload stats to show empty state
                                viewModel.loadStats(selectedTimeRange)
                            },
                            onError = { error ->
                                Toast.makeText(
                                    context,
                                    error,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
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
