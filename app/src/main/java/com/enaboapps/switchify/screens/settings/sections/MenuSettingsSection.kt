package com.enaboapps.switchify.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.MenuSettingsModel

@Composable
fun MenuSection(screenModel: MenuSettingsModel, navController: NavController) {
    Section(titleResId = R.string.settings_section_menu) {
        NavRouteLink(
            titleResId = R.string.screen_title_menu_customization,
            summaryResId = R.string.settings_summary_menu_customization,
            navController = navController,
            route = NavigationRoute.MenuCustomization.name
        )
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