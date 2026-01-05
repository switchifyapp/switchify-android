package com.enaboapps.switchify.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute

@Composable
fun GesturesSettingsSection(navController: NavController) {
    Section(titleResId = R.string.settings_section_gesture) {
        NavRouteLink(
            titleResId = R.string.screen_title_gesture_patterns,
            summaryResId = R.string.gesture_patterns_section_summary,
            navController = navController,
            route = NavigationRoute.GesturePatterns.name
        )
        NavRouteLink(
            titleResId = R.string.screen_title_scrolling_settings,
            summaryResId = R.string.settings_summary_scrolling_settings,
            navController = navController,
            route = NavigationRoute.ScrollingSettings.name
        )
    }
}