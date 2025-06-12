package com.enaboapps.switchify.service.menu.menus.recentapps

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.utils.RecentAppsManager

class RecentAppsMenuStructure(private val accessibilityService: SwitchifyAccessibilityService) {
    
    private val recentAppsManager = RecentAppsManager(accessibilityService)
    
    /**
     * Get recently used apps using RecentAppsManager
     */
    suspend fun getRecentApps(): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        
        // Check if permission is granted
        if (!recentAppsManager.hasUsageStatsPermission()) {
            items.add(MenuItem(
                id = "permission_required",
                textResource = R.string.usage_stats_permission_required,
                action = {
                    // Open permission settings
                    recentAppsManager.openUsageStatsSettings()
                }
            ))
            return items
        }
        
        // Get recent apps
        val recentApps = recentAppsManager.getRecentApps(hoursToLookBack = 10)
        
        if (recentApps.isEmpty()) {
            items.add(MenuItem(
                id = "no_recent_apps",
                textResource = R.string.no_recent_apps_available,
                action = { /* Do nothing */ }
            ))
        } else {
            // Convert RecentApp objects to MenuItems
            recentApps.forEach { app ->
                items.add(recentAppsManager.createMenuItem(app))
            }
        }
        
        return items
    }
    
    fun buildRecentAppsMenuObject(): MenuStructure {
        return MenuStructure(
            id = "recent_apps_menu",
            items = emptyList() // Items will be loaded dynamically
        )
    }
}