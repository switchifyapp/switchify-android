package com.enaboapps.switchify.service.menu.menus.quickapps

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.utils.QuickAppsManager

class QuickAppsMenuStructure(private val accessibilityService: SwitchifyAccessibilityService) {

    private val quickAppsManager = QuickAppsManager(accessibilityService)

    /**
     * Get menu items from preloaded apps
     */
    fun getMenuItems(apps: List<QuickAppsManager.RecentApp>): List<MenuItem> {
        val items = mutableListOf<MenuItem>()

        // Check if permission is granted
        if (!quickAppsManager.hasUsageStatsPermission()) {
            items.add(
                MenuItem(
                    id = "permission_required",
                    labelResource = R.string.usage_stats_permission_required,
                    action = {
                        // Open permission settings
                        quickAppsManager.openUsageStatsSettings()
                    }
                ))
            return items
        }

        if (apps.isEmpty()) {
            items.add(
                MenuItem(
                    id = "no_quick_apps",
                    labelResource = R.string.no_quick_apps_available,
                    action = { /* Do nothing */ }
                ))
        } else {
            // Convert RecentApp objects to MenuItems
            apps.forEach { app ->
                items.add(quickAppsManager.createMenuItem(app))
            }
        }

        return items
    }

    fun buildQuickAppsMenuObject(): MenuStructure {
        return MenuStructure(
            id = "quick_apps_menu",
            items = emptyList() // Items will be loaded dynamically
        )
    }
}