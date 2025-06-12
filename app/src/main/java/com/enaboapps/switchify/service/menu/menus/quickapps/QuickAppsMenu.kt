package com.enaboapps.switchify.service.menu.menus.quickapps

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

/**
 * Menu that displays recently used apps for quick access
 */
class QuickAppsMenu(
    private val accessibilityService: SwitchifyAccessibilityService
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = emptyList(), // Static items are empty, we'll load dynamically
    dynamicLoad = {
        // Load recent apps dynamically
        val quickAppsStructure = QuickAppsMenuStructure(accessibilityService)
        quickAppsStructure.getRecentApps()
    },
    showSystemNavItems = true,
    showNavMenuItems = true
)