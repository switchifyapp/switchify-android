package com.enaboapps.switchify.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.*
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.SettingsScreenModel
import com.enaboapps.switchify.screens.settings.scanning.ScanMethodSelectionSection
import com.enaboapps.switchify.screens.settings.scanning.ScanModeSelectionSection

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsScreenModel = SettingsScreenModel(context)
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    BaseView(
        titleResId = R.string.screen_title_settings,
        navController = navController,
        padding = 0.dp,
        enableScroll = false
    ) {
        TabRow(selectedTabIndex = selectedTabIndex) {
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
            0 -> GeneralSettingsTab(settingsScreenModel, navController)
            1 -> ScanningSettingsTab(navController)
            2 -> SelectionSettingsTab(settingsScreenModel)
            3 -> AboutSection()
        }
    }
}

@Composable
fun GeneralSettingsTab(settingsScreenModel: SettingsScreenModel, navController: NavController) {
    ScrollableView {
        InputSection(navController)
        GesturesSettingsSection(navController)
        MenuSection(settingsScreenModel, navController)
        ActionsSection(navController)
    }
}

@Composable
fun ScanningSettingsTab(navController: NavController) {
    ScrollableView {
        ScanMethodSelectionSection()

        InfoCard(
            titleResId = R.string.settings_info_title_scan_settings,
            descriptionResId = R.string.settings_info_desc_scan_settings
        )

        Section(titleResId = R.string.settings_section_scanning_method) {
            NavRouteLink(
                titleResId = R.string.settings_title_cursor_scan,
                summaryResId = R.string.settings_summary_cursor_scan,
                navController = navController,
                route = NavigationRoute.CursorSettings.name
            )

            NavRouteLink(
                titleResId = R.string.settings_title_item_scan,
                summaryResId = R.string.settings_summary_item_scan,
                navController = navController,
                route = NavigationRoute.ItemScanSettings.name
            )

            NavRouteLink(
                titleResId = R.string.settings_title_radar_scan,
                summaryResId = R.string.settings_summary_radar_scan,
                navController = navController,
                route = NavigationRoute.RadarSettings.name
            )
        }

        ScanModeSelectionSection()

        NavRouteLink(
            titleResId = R.string.settings_title_other_scan,
            summaryResId = R.string.settings_summary_other_scan,
            navController = navController,
            route = NavigationRoute.OtherScanSettings.name
        )

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
fun SelectionSettingsTab(settingsScreenModel: SettingsScreenModel) {
    ScrollableView {
        SelectionSection(settingsScreenModel)
    }
}

@Composable
private fun InputSection(navController: NavController) {
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

@Composable
private fun GesturesSettingsSection(navController: NavController) {
    Section(titleResId = R.string.settings_section_gesture) {
        NavRouteLink(
            titleResId = R.string.settings_title_gesture_settings,
            summaryResId = R.string.settings_summary_gesture_settings,
            navController = navController,
            route = NavigationRoute.GestureSettings.name
        )
    }
}

@Composable
private fun MenuSection(screenModel: SettingsScreenModel, navController: NavController) {
    Section(titleResId = R.string.settings_section_menu) {
        NavRouteLink(
            titleResId = R.string.settings_title_menu_size,
            summaryResId = R.string.settings_summary_menu_size,
            navController = navController,
            route = NavigationRoute.MenuSize.name
        )
        PreferenceSwitch(
            titleResId = R.string.settings_title_menu_transparency,
            summaryResId = R.string.settings_summary_menu_transparency,
            checked = screenModel.menuTransparency.value == true,
            onCheckedChange = {
                screenModel.setMenuTransparency(it)
            }
        )
    }
}

@Composable
private fun ActionsSection(navController: NavController) {
    Section(titleResId = R.string.settings_section_actions) {
        NavRouteLink(
            titleResId = R.string.settings_title_my_actions,
            summaryResId = R.string.settings_summary_my_actions,
            navController = navController,
            route = NavigationRoute.MyActions.name
        )
    }
}

@Composable
private fun SelectionSection(screenModel: SettingsScreenModel) {
    val autoSelect = screenModel.autoSelect.observeAsState()
    Section(titleResId = R.string.settings_section_selection) {
        PreferenceSwitch(
            titleResId = R.string.settings_title_auto_select,
            summaryResId = R.string.settings_summary_auto_select,
            checked = screenModel.autoSelect.value == true,
            onCheckedChange = {
                screenModel.setAutoSelect(it)
            }
        )
        if (autoSelect.value == true) {
            PreferenceTimeStepper(
                value = screenModel.autoSelectDelay.value ?: 0,
                titleResId = R.string.settings_title_auto_select_delay,
                summaryResId = R.string.settings_summary_auto_select_delay,
                min = 100,
                max = 100000
            ) {
                screenModel.setAutoSelectDelay(it)
            }
        }
        PreferenceSwitch(
            titleResId = R.string.settings_title_directly_select_keyboard,
            summaryResId = R.string.settings_summary_directly_select_keyboard,
            checked = screenModel.directlySelectKeyboardKeys.value == true,
            onCheckedChange = {
                screenModel.setDirectlySelectKeyboardKeys(it)
            }
        )
        PreferenceSwitch(
            titleResId = R.string.settings_title_assisted_selection,
            summaryResId = R.string.settings_summary_assisted_selection,
            checked = screenModel.assistedSelection.value == true,
            onCheckedChange = {
                screenModel.setAssistedSelection(it)
            }
        )
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName

    val websiteUrl = "https://switchifyapp.com"
    val privacyPolicyUrl = "https://www.switchifyapp.com/privacy"

    ScrollableView {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Version $version",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        FullWidthButton(
            textResId = R.string.button_website,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl)))
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        FullWidthButton(
            textResId = R.string.button_privacy_policy,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)))
            }
        )
    }
}
