package com.enaboapps.switchify.screens.settings.menu

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

@Composable
fun MenuItemCustomizationScreen(navController: NavController) {
    val context = LocalContext.current
    val preferenceManager = PreferenceManager(context)
    val menuStructureHolder = MenuStructureHolder()

    val items: MutableList<MenuItem> = mutableListOf()
    items.addAll(menuStructureHolder.mainMenuObject.getMenuItems())
    items.add(menuStructureHolder.toggleGestureLockMenuItem)
    items.addAll(menuStructureHolder.buildDeviceMenuObject().getMenuItems())
    val uniqueItems = items.distinctBy { it.id }

    BaseView(
        titleResId = R.string.screen_title_customize_menu,
        navController = navController
    ) {
        uniqueItems.forEach { menuItem ->
            val isVisible = remember { mutableStateOf(menuItem.isVisible(context)) }
            PreferenceSwitch(
                titleResId = menuItem.textResource ?: R.string.unknown,
                summaryResId = if (isVisible.value) R.string.menu_item_state_shown else R.string.menu_item_state_hidden,
                checked = isVisible.value,
                onCheckedChange = {
                    isVisible.value = it
                    preferenceManager.setMenuItemVisibility(menuItem.id, it)
                }
            )
        }
    }
}
