package com.enaboapps.switchify.service.menu.menus.favouriteapps

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.utils.FavouriteAppsManager
import kotlinx.coroutines.CoroutineScope

class FavouriteAppsMenuStructure(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val coroutineScope: CoroutineScope
) {

    private val favouriteAppsManager = FavouriteAppsManager(accessibilityService)

    /**
     * Get menu items from favourite apps list.
     */
    fun getMenuItems(apps: List<FavouriteAppsManager.FavouriteApp>): List<MenuItem> {
        val items = mutableListOf<MenuItem>()

        if (apps.isEmpty()) {
            items.add(
                MenuItem(
                    id = "no_favourite_apps",
                    labelResource = R.string.no_favourite_apps,
                    descriptionResource = R.string.menu_item_no_favourite_apps_description,
                    action = { /* Do nothing */ }
                )
            )
        } else {
            apps.forEach { app ->
                items.add(favouriteAppsManager.createMenuItem(app))
            }
        }

        return items
    }

    /**
     * Build a placeholder MenuStructure for the favourite apps menu whose items are loaded dynamically.
     *
     * @return A MenuStructure with id "favourite_apps_menu", an empty items list, and context set to the accessibility service.
     */
    fun buildFavouriteAppsMenuObject(): MenuStructure {
        return MenuStructure(
            id = "favourite_apps_menu",
            items = emptyList(),
            context = accessibilityService,
            coroutineScope = coroutineScope
        )
    }
}
