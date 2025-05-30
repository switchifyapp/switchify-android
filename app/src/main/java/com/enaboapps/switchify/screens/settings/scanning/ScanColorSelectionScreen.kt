package com.enaboapps.switchify.screens.settings.scanning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.service.scanning.ScanColorManager

@Composable
fun ScanColorSelectionScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scanColorSets = ScanColorManager.SCAN_COLOR_SETS
    var currentScanColorSet by remember {
        mutableStateOf(ScanColorManager.getScanColorSetFromPreferences(context))
    }

    BaseView(
        titleResId = R.string.screen_title_scan_colors,
        navController = navController
    ) {
        Picker(
            titleResId = R.string.screen_title_scan_colors,
            selectedItem = currentScanColorSet,
            items = scanColorSets,
            onItemSelected = { colorSet ->
                ScanColorManager.setScanColorSetToPreferences(context, colorSet.getName())
                currentScanColorSet = colorSet
            },
            itemToString = { it.getName() },
            itemDescription = { it.getDescription() }
        )
    }
}