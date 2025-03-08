package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.scanning.ScanMode

@Composable
fun ScanModeSelectionSection(
    navController: NavController,
    onChange: ((String) -> Unit)? = null
) {
    val preferenceManager = PreferenceManager(LocalContext.current)
    var currentMode by remember {
        mutableStateOf(
            ScanMode.fromId(
                preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE)
            )
        )
    }

    val setScanMode = { mode: ScanMode ->
        preferenceManager.setStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE, mode.id)
        currentMode = mode
    }

    Section(titleResId = R.string.section_title_scanning_mode) {
        Picker(
            titleResId = R.string.picker_title_select_scan_mode,
            selectedItem = currentMode,
            items = ScanMode.modes.toList(),
            onItemSelected = { mode ->
                setScanMode(mode)
                onChange?.invoke(mode.id)
            },
            itemToString = { it.getModeName() },
            itemDescription = { it.getModeDescription() }
        )
        AnimatedVisibility(
            visible = currentMode.id == ScanMode.Modes.MODE_AUTO,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                NavRouteLink(
                    titleResId = R.string.screen_title_scan_speeds,
                    summaryResId = R.string.screen_summary_scan_speeds,
                    navController = navController,
                    route = NavigationRoute.ScanSpeeds.name
                )
            }
        }
        AnimatedVisibility(
            visible = currentMode.id == ScanMode.Modes.MODE_MANUAL,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                NavRouteLink(
                    titleResId = R.string.screen_title_manual_settings,
                    summaryResId = R.string.screen_summary_manual_settings,
                    navController = navController,
                    route = NavigationRoute.ManualScanSettings.name
                )
            }
        }
    }
}