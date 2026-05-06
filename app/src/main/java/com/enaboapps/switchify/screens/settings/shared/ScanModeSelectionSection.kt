package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.AutoScanDemo
import com.enaboapps.switchify.components.ManualScanDemo
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.ScanModeSettingsModel
import com.enaboapps.switchify.service.scanning.ScanMode

@Composable
fun ScanModeSelectionSection(
    navController: NavController,
    onChange: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: ScanModeSettingsModel = viewModel {
        ScanModeSettingsModel(context)
    }
    val uiState = viewModel.uiState.collectAsState()

    Section(titleResId = R.string.section_title_scanning_mode) {
        Picker(
            titleResId = R.string.picker_title_select_scan_mode,
            selectedItem = uiState.value.currentMode,
            items = ScanMode.modes.toList(),
            onItemSelected = { mode ->
                viewModel.selectMode(mode)
                onChange?.invoke(mode.id)
            },
            itemToString = { it.getModeName() },
            itemDescription = { it.getModeDescription() }
        )

        // Visual demonstration of selected scan mode
        AnimatedVisibility(
            visible = uiState.value.currentMode.id == ScanMode.Modes.MODE_AUTO,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Panel(modifier = Modifier.padding(top = 24.dp, start = 32.dp, end = 32.dp)) {
                Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)) {
                    val userScanDelay = 1000L
                    AutoScanDemo(
                        color = MaterialTheme.colorScheme.primary,
                        scanDelay = userScanDelay
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.value.currentMode.id == ScanMode.Modes.MODE_MANUAL,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Panel(modifier = Modifier.padding(top = 24.dp, start = 32.dp, end = 32.dp)) {
                Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)) {
                    ManualScanDemo(
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = uiState.value.currentMode.id == ScanMode.Modes.MODE_AUTO,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(20.dp))
                NavRouteLink(
                    titleResId = R.string.screen_title_scan_speeds,
                    summaryResId = R.string.screen_summary_scan_speeds,
                    navController = navController,
                    route = NavigationRoute.ScanSpeeds.name
                )
            }
        }
        AnimatedVisibility(
            visible = uiState.value.currentMode.id == ScanMode.Modes.MODE_MANUAL,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(20.dp))
                NavRouteLink(
                    titleResId = R.string.screen_title_manual_settings,
                    summaryResId = R.string.screen_summary_manual_settings,
                    navController = navController,
                    route = NavigationRoute.ManualScanSettings.name
                )
            }
        }
        AnimatedVisibility(
            visible = uiState.value.currentMode.id == ScanMode.Modes.MODE_AUTO,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(20.dp))
                NavRouteLink(
                    titleResId = R.string.screen_title_auto_scan_settings,
                    summaryResId = R.string.screen_summary_auto_scan_settings,
                    navController = navController,
                    route = NavigationRoute.AutoScanSettings.name
                )
            }
        }
    }
}
