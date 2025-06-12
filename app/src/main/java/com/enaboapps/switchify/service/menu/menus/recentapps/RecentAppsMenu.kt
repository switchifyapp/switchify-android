package com.enaboapps.switchify.service.menu.menus.recentapps

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

/**
 * Menu that displays recently used apps
 */
class RecentAppsMenu(
    private val accessibilityService: SwitchifyAccessibilityService
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = emptyList(), // Static items are empty, we'll load dynamically
    dynamicLoad = {
        // Load recent apps dynamically
        val recentAppsStructure = RecentAppsMenuStructure(accessibilityService)
        recentAppsStructure.getRecentApps()
    },
    showSystemNavItems = true,
    showNavMenuItems = true
)