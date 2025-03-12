package com.enaboapps.switchify.service.menu.menus

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuView
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

/**
 * This class represents a base menu
 * @property accessibilityService The accessibility service
 * @property items The menu items
 * @property showSystemNavItems Whether to show system navigation items
 * @property showNavMenuItems Whether to show navigation menu items
 */
open class BaseMenu(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val items: List<MenuItem>,
    private val showSystemNavItems: Boolean = true,
    private val showNavMenuItems: Boolean = true
) {
    /**
     * Get the menu items
     * @return The menu items
     */
    fun getMenuItems(): List<MenuItem> {
        return items
    }

    /**
     * Determine whether to show system navigation items
     * @return true if system navigation items should be shown, false otherwise
     */
    fun shouldShowSystemNavItems(): Boolean {
        return showSystemNavItems
    }

    /**
     * Determine whether to show navigation menu items
     * @return true if navigation menu items should be shown, false otherwise
     */
    fun shouldShowNavMenuItems(): Boolean {
        return showNavMenuItems
    }

    /**
     * Build the system navigation items
     * @return The system navigation items
     */
    fun buildSystemNavItems(): List<MenuItem> {
        return MenuStructureHolder(accessibilityService).systemNavItems
    }

    /**
     * Build the navigation menu items
     * @return The navigation menu items
     */
    fun buildNavMenuItems(): List<MenuItem> {
        return MenuStructureHolder(accessibilityService).menuManipulatorItems
    }

    /**
     * Build the menu view
     * @return The menu view
     */
    fun build(): MenuView {
        return MenuView(accessibilityService, this)
    }
}