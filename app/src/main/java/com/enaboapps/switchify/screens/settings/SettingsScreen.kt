package com.enaboapps.switchify.screens.settings

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.SettingsScreenModel
import com.enaboapps.switchify.screens.settings.shared.ScanModeSelectionSection
import com.enaboapps.switchify.screens.settings.techniques.AccessTechniqueSelector

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsScreenModel: SettingsScreenModel = viewModel { SettingsScreenModel(context) }
    val preferenceManager = remember { PreferenceManager(context) }
    
    // Load the saved tab index, default to 0 if not found
    var selectedTabIndex by remember { 
        val savedIndex = preferenceManager.getIntegerValue(PreferenceManager.PREFERENCE_KEY_SETTINGS_TAB, 0)
        // Ensure the saved index is within valid range (0-3)
        mutableIntStateOf(savedIndex.coerceIn(0, 3))
    }
    
    // Save tab selection when it changes
    LaunchedEffect(selectedTabIndex) {
        preferenceManager.setIntegerValue(PreferenceManager.PREFERENCE_KEY_SETTINGS_TAB, selectedTabIndex)
    }

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
        GesturesSettingsSection(navController)
        AISection(settingsScreenModel)
        MenuSection(settingsScreenModel)
    }
}

@Composable
fun ScanningSettingsTab(navController: NavController) {
    ScrollableView {
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
        NavRouteLink(
            titleResId = R.string.screen_title_gesture_patterns,
            summaryResId = R.string.gesture_patterns_description,
            navController = navController,
            route = NavigationRoute.GesturePatterns.name
        )
    }
}

@Composable
private fun AISection(screenModel: SettingsScreenModel) {
    Section(titleResId = R.string.settings_section_ai) {
        PreferenceSwitch(
            titleResId = R.string.settings_title_ai_suggestions,
            summaryResId = R.string.settings_summary_ai_suggestions,
            isRestrictedToPro = true,
            checked = screenModel.aiSuggestionsEnabled.value == true,
            onCheckedChange = {
                screenModel.setAiSuggestionsEnabled(it)
            }
        )
        PreferenceSwitch(
            titleResId = R.string.settings_title_ai_visual_analysis,
            summaryResId = R.string.settings_summary_ai_visual_analysis,
            isRestrictedToPro = true,
            checked = screenModel.aiVisualAnalysisEnabled.value == true,
            onCheckedChange = {
                screenModel.setAiVisualAnalysisEnabled(it)
            }
        )
    }
}

@Composable
private fun MenuSection(screenModel: SettingsScreenModel) {
    Section(titleResId = R.string.settings_section_menu) {
        PreferenceSwitch(
            titleResId = R.string.settings_title_menu_transparency,
            summaryResId = R.string.settings_summary_menu_transparency,
            checked = screenModel.menuTransparency.value == true,
            onCheckedChange = {
                screenModel.setMenuTransparency(it)
            }
        )
        PreferenceValueSelector(
            value = screenModel.menuRowsPerPage.value ?: 2,
            titleResId = R.string.settings_title_menu_rows_per_page,
            summaryResId = R.string.settings_summary_menu_rows_per_page,
            values = intArrayOf(1, 2, 3, 4),
            buttonLabelFormatter = { it.toString() },
            displayFormatter = { it.toString() },
            onValueChanged = {
                screenModel.setMenuRowsPerPage(it)
            }
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
                context.startActivity(Intent(Intent.ACTION_VIEW, websiteUrl.toUri()))
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        FullWidthButton(
            textResId = R.string.button_privacy_policy,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, privacyPolicyUrl.toUri()))
            }
        )
    }
}
