package com.enaboapps.switchify.screens.settings.scanning

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.BlockPointScanSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.BlockRadarSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.ItemScanSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.PrecisionPointScanSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.RadarSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.SinglePointScanSpeedStepper
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings
import com.enaboapps.switchify.service.techniques.radar.RadarSettings

@Composable
fun ScanSpeedsScreen(navController: NavController) {
    // Initialize settings to prevent crashes when accessing speed components
    PointScanSettings.init(LocalContext.current)
    RadarSettings.init(LocalContext.current)
    BaseView(
        titleResId = R.string.screen_title_scan_speeds,
        navController = navController
    ) {
        Section(titleResId = R.string.access_technique_point_scan) {
            if (PointScanSettings.isBlockMode()) {
                PrecisionPointScanSpeedStepper()
                BlockPointScanSpeedStepper()
            } else {
                SinglePointScanSpeedStepper()
            }
        }

        Section(titleResId = R.string.access_technique_item_scan) {
            ItemScanSpeedStepper()
        }

        Section(titleResId = R.string.access_technique_radar) {
            RadarSpeedStepper()
            if (RadarSettings.isBlockMode()) {
                BlockRadarSpeedStepper()
            }
        }
    }
}
