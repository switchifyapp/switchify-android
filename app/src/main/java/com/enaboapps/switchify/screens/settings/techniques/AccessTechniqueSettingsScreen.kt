package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.AnimatedTabContent
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PillTab
import com.enaboapps.switchify.components.PillTabRow
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

        PillTabRow(
            tabs = tabs.map { PillTab(stringResource(it)) },
            selectedIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        AnimatedTabContent(targetState = tabs.getOrNull(selectedTabIndex)) { currentTab ->
            when (currentTab) {
                R.string.settings_tab_point_scan -> PointScanSettingsTab()
                R.string.settings_tab_radar -> RadarSettingsTab()
                R.string.settings_tab_item_scan -> ItemScanSettingsTab()
            }
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

