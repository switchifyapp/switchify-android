package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.ScrollableView

@Composable
fun AccessTechniqueSettingsScreen(navController: NavController) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    BaseView(
        titleResId = R.string.screen_title_access_technique_settings,
        navController = navController,
        padding = 0.dp,
        enableScroll = false
    ) {
        val tabs = listOf(
            R.string.settings_tab_point_scan,
            R.string.settings_tab_radar,
            R.string.settings_tab_item_scan
        )

        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, tabResId ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(stringResource(tabResId)) }
                )
            }
        }

        val currentTab = tabs.getOrNull(selectedTabIndex)
        when (currentTab) {
            R.string.settings_tab_point_scan -> PointScanSettingsTab()
            R.string.settings_tab_radar -> RadarSettingsTab()
            R.string.settings_tab_item_scan -> ItemScanSettingsTab()
        }
    }
}

@Composable
private fun PointScanSettingsTab() {
    ScrollableView {
        PointScanSettingsView()
    }
}

@Composable
private fun RadarSettingsTab() {
    ScrollableView {
        RadarSettingsView()
    }
}

@Composable
private fun ItemScanSettingsTab() {
    ScrollableView {
        ItemScanSettingsView()
    }
}

