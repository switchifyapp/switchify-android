package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
@Composable
fun SwitchesScreen(navController: NavController) {
    BaseView(
        titleResId = R.string.screen_title_switches,
        navController = navController,
        enableScroll = false
    ) {
        ScrollableView {
            Section(titleResId = R.string.section_title_switches) {
                NavRouteLink(
                    titleResId = R.string.screen_title_external_switches,
                    summaryResId = R.string.external_switches_summary,
                    navController = navController,
                    route = NavigationRoute.ExternalSwitches.name
                )
                NavRouteLink(
                    titleResId = R.string.screen_title_camera_switches,
                    summaryResId = R.string.camera_switches_summary,
                    navController = navController,
                    route = NavigationRoute.CameraSwitches.name
                )
            }
        }
    }
}

