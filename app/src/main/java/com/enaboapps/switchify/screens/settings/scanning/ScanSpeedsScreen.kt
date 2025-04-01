package com.enaboapps.switchify.screens.settings.scanning

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.BlockCursorSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.ItemScanSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.PrecisionCursorSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.RadarSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.SingleCursorSpeedStepper
import com.enaboapps.switchify.service.techniques.cursor.CursorSettings

@Composable
fun ScanSpeedsScreen(navController: NavController) {
    BaseView(
        titleResId = R.string.screen_title_scan_speeds,
        navController = navController
    ) {
        Section(titleResId = R.string.access_technique_cursor) {
            if (CursorSettings.isBlockMode()) {
                PrecisionCursorSpeedStepper()
                BlockCursorSpeedStepper()
            } else {
                SingleCursorSpeedStepper()
            }
        }

        Section(titleResId = R.string.access_technique_item_scan) {
            ItemScanSpeedStepper()
        }

        Section(titleResId = R.string.access_technique_radar) {
            RadarSpeedStepper()
        }
    }
}