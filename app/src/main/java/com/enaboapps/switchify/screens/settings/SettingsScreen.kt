package com.enaboapps.switchify.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.auth.repository.AuthRepository
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.MenuSettingsModel
import com.enaboapps.switchify.screens.settings.models.SelectionSettingsModel
import com.enaboapps.switchify.screens.settings.models.AboutSettingsModel
import com.enaboapps.switchify.screens.settings.sections.AboutSection
import com.enaboapps.switchify.screens.settings.sections.BehaviourSection
import com.enaboapps.switchify.screens.settings.sections.GesturesSettingsSection
import com.enaboapps.switchify.screens.settings.sections.InputSection
import com.enaboapps.switchify.screens.settings.sections.MenuSection
import com.enaboapps.switchify.screens.settings.sections.SelectionSection
import com.enaboapps.switchify.screens.settings.shared.ScanModeSelectionSection
import com.enaboapps.switchify.screens.settings.techniques.AccessTechniqueSelector

data class SettingsRouteLinkSpec(
    @param:StringRes val titleResId: Int,
    @param:StringRes val summaryResId: Int,
    val route: String
)

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val aboutSettingsModel: AboutSettingsModel =
        viewModel { AboutSettingsModel(context) }
    val selectionSettingsModel: SelectionSettingsModel =
        viewModel { SelectionSettingsModel(context) }
    val menuSettingsModel: MenuSettingsModel = viewModel { MenuSettingsModel(context) }
    val preferenceManager = remember { PreferenceManager(context) }

    // Load the saved tab index, default to 0 if not found
    var selectedTabIndex by remember {
        val savedIndex =
            preferenceManager.getIntegerValue(PreferenceManager.PREFERENCE_KEY_SETTINGS_TAB, 0)
        // Ensure the saved index is within valid range (0-3)
        mutableIntStateOf(savedIndex.coerceIn(0, 3))
    }

    // Save tab selection when it changes
    LaunchedEffect(selectedTabIndex) {
        preferenceManager.setIntegerValue(
            PreferenceManager.PREFERENCE_KEY_SETTINGS_TAB,
            selectedTabIndex
        )
    }

    BaseView(
        titleResId = R.string.screen_title_settings,
        navController = navController,
        padding = 0.dp,
        enableScroll = false
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            listOf(
                R.string.settings_tab_general,
                R.string.settings_tab_scanning,
                R.string.settings_tab_selection,
                R.string.settings_tab_about
            ).forEachIndexed { index, tabResId ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(stringResource(tabResId)) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> GeneralSettingsTab(menuSettingsModel, navController)
            1 -> ScanningSettingsTab(navController)
            2 -> SelectionSettingsTab(selectionSettingsModel)
            3 -> AboutSection(aboutSettingsModel, navController)
        }
    }
}

/**
 * Renders the General settings tab including the account action and related setting sections.
 *
 * The account link displays an "Account" entry and navigates to the Account route when a user is signed in;
 * otherwise it displays a "Sign in" entry and navigates to the Authentication route.
 *
 * @param menuSettingsModel Provides state and actions for the menu settings section.
 * @param navController NavController used to navigate from links within this tab.
 */
@Composable
fun GeneralSettingsTab(menuSettingsModel: MenuSettingsModel, navController: NavController) {
    val authRepository = AuthRepository.instance
    val isSignedIn = authRepository.isUserSignedIn()

    ScrollableView {
        Section(titleResId = R.string.settings_section_account) {
            NavRouteLink(
                titleResId = if (isSignedIn) R.string.settings_title_account else R.string.settings_title_sign_in,
                summaryResId = if (isSignedIn) R.string.settings_summary_account else R.string.settings_summary_sign_in,
                navController = navController,
                route = if (isSignedIn) NavigationRoute.Account.name else NavigationRoute.Authentication.name
            )
        }
        Section(titleResId = R.string.settings_section_usage) {
            NavRouteLink(
                titleResId = R.string.settings_title_stats,
                summaryResId = R.string.settings_summary_stats,
                navController = navController,
                route = NavigationRoute.Stats.name
            )
        }
        Section(titleResId = R.string.settings_section_ai_model) {
            NavRouteLink(
                titleResId = R.string.settings_title_ai_model,
                summaryResId = R.string.settings_summary_ai_model,
                navController = navController,
                route = NavigationRoute.AiModel.name
            )
        }
        val pcSettingsLink = pcSettingsRouteLinkSpec()
        Section(titleResId = R.string.pc_settings_title) {
            NavRouteLink(
                titleResId = pcSettingsLink.titleResId,
                summaryResId = pcSettingsLink.summaryResId,
                navController = navController,
                route = pcSettingsLink.route
            )
        }
        InputSection(navController)
        BehaviourSection(navController)
        GesturesSettingsSection(navController)
        MenuSection(menuSettingsModel, navController)
    }
}

fun pcSettingsRouteLinkSpec(): SettingsRouteLinkSpec {
    return SettingsRouteLinkSpec(
        titleResId = R.string.pc_settings_title,
        summaryResId = R.string.pc_settings_link_summary,
        route = NavigationRoute.PcSettings.name
    )
}

@Composable
fun ScanningSettingsTab(navController: NavController) {
    ScrollableView {
        Section(titleResId = R.string.settings_section_access_techniques) {
            AccessTechniqueSelector()
            Spacer(modifier = Modifier.padding(vertical = 8.dp))
            NavRouteLink(
                titleResId = R.string.settings_title_access_technique,
                summaryResId = R.string.settings_summary_access_technique,
                navController = navController,
                route = NavigationRoute.AccessTechniqueSettings.name
            )
        }

        ScanModeSelectionSection(navController)

        Section(titleResId = R.string.settings_section_scan_appearance) {
            NavRouteLink(
                titleResId = R.string.settings_title_scan_color,
                summaryResId = R.string.settings_summary_scan_color,
                navController = navController,
                route = NavigationRoute.ScanColor.name
            )
        }
    }
}

@Composable
fun SelectionSettingsTab(selectionSettingsModel: SelectionSettingsModel) {
    ScrollableView {
        SelectionSection(selectionSettingsModel)
    }
}





