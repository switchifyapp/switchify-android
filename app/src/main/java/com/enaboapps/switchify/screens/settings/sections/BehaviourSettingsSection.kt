package com.enaboapps.switchify.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute

@Composable
fun BehaviourSection(navController: NavController) {
    Section(titleResId = R.string.settings_section_behaviour) {
        NavRouteLink(
            titleResId = R.string.settings_title_pause,
            summaryResId = R.string.settings_summary_pause,
            navController = navController,
            route = NavigationRoute.PauseSettings.name
        )
    }
}
