package com.enaboapps.switchify.screens.settings.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PanelListRow
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.theme.Dimens

/**
 * Entry point for menu customization. Lists every customizable menu as a
 * tappable row; picking one opens [MenuCustomizationScreen] for that menu.
 * Static — no view model needed.
 */
@Composable
fun MenuCustomizationPickerScreen(navController: NavController) {
    BaseView(
        titleResId = R.string.screen_title_menu_customization,
        navController = navController
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            Text(
                text = stringResource(R.string.menu_customization_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            MenuConstants.customizableMenus.forEach { (menuId, nameResId) ->
                PanelListRow(
                    titleResId = nameResId,
                    onClick = {
                        navController.navigate(
                            "${NavigationRoute.MenuCustomizationEdit.name}/$menuId"
                        )
                    }
                )
            }
        }
    }
}
