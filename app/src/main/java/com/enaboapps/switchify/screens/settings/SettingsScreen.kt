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
import com.enaboapps.switchify.screens.settings.models.AISettingsModel
import com.enaboapps.switchify.screens.settings.models.MenuSettingsModel
import com.enaboapps.switchify.screens.settings.models.SelectionSettingsModel
import com.enaboapps.switchify.screens.settings.models.SettingsScreenModel
import com.enaboapps.switchify.screens.settings.sections.AboutSection
import com.enaboapps.switchify.screens.settings.sections.AISection
import com.enaboapps.switchify.screens.settings.sections.GesturesSettingsSection
import com.enaboapps.switchify.screens.settings.sections.InputSection
import com.enaboapps.switchify.screens.settings.sections.MenuSection
import com.enaboapps.switchify.screens.settings.sections.SelectionSection
import com.enaboapps.switchify.screens.settings.shared.ScanModeSelectionSection
import com.enaboapps.switchify.screens.settings.techniques.AccessTechniqueSelector

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsScreenModel: SettingsScreenModel = viewModel { SettingsScreenModel(context) }
    val selectionSettingsModel: SelectionSettingsModel = viewModel { SelectionSettingsModel(context) }
    val aiSettingsModel: AISettingsModel = viewModel { AISettingsModel(context) }
    val menuSettingsModel: MenuSettingsModel = viewModel { MenuSettingsModel(context) }
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
            0 -> GeneralSettingsTab(aiSettingsModel, menuSettingsModel, navController)
            1 -> ScanningSettingsTab(navController)
            2 -> SelectionSettingsTab(selectionSettingsModel)
            3 -> AboutSection()
        }
    }
}

@Composable
fun GeneralSettingsTab(aiSettingsModel: AISettingsModel, menuSettingsModel: MenuSettingsModel, navController: NavController) {
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
        AISection(aiSettingsModel)
        MenuSection(menuSettingsModel)
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
fun SelectionSettingsTab(selectionSettingsModel: SelectionSettingsModel) {
    ScrollableView {
        SelectionSection(selectionSettingsModel)
    }
}






