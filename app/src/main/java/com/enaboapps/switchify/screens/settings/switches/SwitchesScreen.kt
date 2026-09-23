package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.PanelListRow
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.switches.profiles.SwitchProfileRepository

@Composable
fun SwitchesScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { SwitchProfileRepository.getInstance(context) }
    val document by repository.document.collectAsState()
    val profileContext = rememberSwitchProfileContext(null)
    LaunchedEffect(Unit) { repository.initialize() }
    HandleMissingSwitchProfile(profileContext, navController)

    BaseView(
        titleResId = R.string.screen_title_switches,
        navController = navController,
        navBarTrailingContent = {
            SwitchProfileIndicator(profileContext, navController)
        },
        enableScroll = false
    ) {
        ScrollableView {
            Section(titleResId = R.string.screen_title_switch_profiles) {
                PanelListRow(
                    titleResId = R.string.screen_title_switch_profiles,
                    runtimeSummary = stringResource(
                        R.string.switch_profiles_entry_summary,
                        document.profiles.firstOrNull { it.id == document.activeProfileId }?.name
                            ?: "Default"
                    ),
                    onClick = { navController.navigate(NavigationRoute.SwitchProfiles.name) }
                )
            }
            profileContext.targetProfileId?.let { targetProfileId ->
                Section(titleResId = R.string.section_title_switches) {
                    NavRouteLink(
                        titleResId = R.string.screen_title_external_switches,
                        summaryResId = R.string.external_switches_summary,
                        navController = navController,
                        route = SwitchProfileRoutes.externalSwitches(targetProfileId)
                    )
                    NavRouteLink(
                        titleResId = R.string.screen_title_camera_switches,
                        summaryResId = R.string.camera_switches_summary,
                        navController = navController,
                        route = SwitchProfileRoutes.cameraSwitches(targetProfileId)
                    )
                }
            }
        }
    }
}

