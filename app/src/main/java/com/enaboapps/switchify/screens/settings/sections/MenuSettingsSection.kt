package com.enaboapps.switchify.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.MenuSettingsModel

/**
 * Displays the "Menu" settings section containing a link to menu customization and the
 * menu-transparency toggle. The rows-per-page selector was removed when the service menu
 * switched from a grid to a single radial ring (see [com.enaboapps.switchify.service.menu.MenuView]) —
 * the underlying preference key is still defined for backward compatibility but unread.
 *
 * @param screenModel Provides current menu settings state and actions to update those settings.
 * @param navController NavController used to navigate to the menu customization screen.
 */
@Composable
fun MenuSection(screenModel: MenuSettingsModel, navController: NavController) {
    Section(titleResId = R.string.settings_section_menu) {
        NavRouteLink(
            titleResId = R.string.favourite_apps_title,
            summaryResId = R.string.favourite_apps_summary,
            navController = navController,
            route = NavigationRoute.FavouriteApps.name
        )
        NavRouteLink(
            titleResId = R.string.screen_title_menu_customization,
            summaryResId = R.string.settings_summary_menu_customization,
            navController = navController,
            route = NavigationRoute.MenuCustomization.name
        )

        // Resolve localized labels in the composable scope so the
        // non-composable formatter lambdas below can capture them.
        val standardLabel = stringResource(R.string.menu_size_standard)
        val largeLabel = stringResource(R.string.menu_size_large)
        val extraLargeLabel = stringResource(R.string.menu_size_extra_large)
        val sizeLabel: (Int) -> String = { percent ->
            when (percent) {
                100 -> standardLabel
                125 -> largeLabel
                150 -> extraLargeLabel
                else -> "$percent%"
            }
        }

        PreferenceValueSelector(
            value = screenModel.menuSizeScale.value ?: 100,
            titleResId = R.string.settings_title_menu_size,
            summaryResId = R.string.settings_summary_menu_size,
            values = intArrayOf(100, 125, 150),
            buttonLabelFormatter = sizeLabel,
            displayFormatter = sizeLabel,
            onValueChanged = { screenModel.setMenuSizeScale(it) }
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