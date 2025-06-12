package com.enaboapps.switchify.service.menu.menus.quickapps

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.utils.QuickAppsManager

/**
 * Menu that displays recently used apps for quick access
 */
class QuickAppsMenu(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val apps: List<QuickAppsManager.RecentApp>
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = QuickAppsMenuStructure(accessibilityService).getMenuItems(apps),
    showSystemNavItems = true,
    showNavMenuItems = true
)