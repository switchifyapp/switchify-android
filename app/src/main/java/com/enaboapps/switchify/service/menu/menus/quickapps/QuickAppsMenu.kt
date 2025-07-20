package com.enaboapps.switchify.service.menu.menus.quickapps

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.utils.QuickAppsManager

/**
 * Menu that displays recently used apps for quick access with optimized loading
 */
class QuickAppsMenu(
    private val accessibilityService: SwitchifyAccessibilityService
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = emptyList(),
    dynamicLoad = {
        val quickAppsManager = QuickAppsManager(accessibilityService)
        
        // Use intelligent cache-first loading for optimal performance
        val apps = if (quickAppsManager.getCachedApps().isNotEmpty()) {
            quickAppsManager.getCachedApps()
        } else {
            quickAppsManager.getRecentApps()
        }
        
        QuickAppsMenuStructure(accessibilityService).getMenuItems(apps)
    },
    showSystemNavItems = true,
    showNavMenuItems = true
)