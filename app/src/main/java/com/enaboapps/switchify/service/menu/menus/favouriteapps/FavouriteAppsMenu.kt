package com.enaboapps.switchify.service.menu.menus.favouriteapps

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.utils.FavouriteAppsManager

/**
 * Menu that displays user-curated favourite apps for quick access.
 */
class FavouriteAppsMenu(
    private val accessibilityService: SwitchifyAccessibilityService
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = emptyList(),
    dynamicLoad = {
        val favouriteAppsManager = FavouriteAppsManager(accessibilityService)
        val apps = favouriteAppsManager.getFavouriteApps()

        FavouriteAppsMenuStructure(
            accessibilityService,
            accessibilityService.getServiceScope()
        ).getMenuItems(apps)
    },
    showNavMenuItems = true
)
