package com.enaboapps.switchify.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute

@Composable
fun InputSection(navController: NavController) {
    Section(titleResId = R.string.settings_section_input) {
        NavRouteLink(
            titleResId = R.string.settings_title_switches,
            summaryResId = R.string.settings_summary_switches,
            navController = navController,
            route = NavigationRoute.Switches.name
        )
        NavRouteLink(
            titleResId = R.string.settings_title_switch_stability,
            summaryResId = R.string.settings_summary_switch_stability,
            navController = navController,
            route = NavigationRoute.SwitchStability.name
        )
    }
}