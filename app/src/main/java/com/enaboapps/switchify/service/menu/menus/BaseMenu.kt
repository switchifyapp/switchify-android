package com.enaboapps.switchify.service.menu.menus

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.MenuView
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder
import com.enaboapps.switchify.service.menu.structure.MenuUserItemsHelper

/**
 * This class represents a base menu
 * @property accessibilityService The accessibility service
 * @property items The menu items
 * @property menuId The menu ID for loading user-added items (if applicable)
 * @property dynamicLoad The suspend function that loads dynamic menu items
 * @property showSystemNavItems Whether to show system navigation items
 * @property showNavMenuItems Whether to show navigation menu items
 */
open class BaseMenu(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val items: List<MenuItem>,
    private val menuId: String? = null,
    private val dynamicLoad: (suspend () -> List<MenuItem>)? = null,
    private val showNavMenuItems: Boolean = true
) {
    /**
     * Get the menu items with automatic previous menu button when applicable
     * and user-added items if a menuId is provided
     * @return The menu items with previous menu button prepended if not at first menu
     */
    suspend fun getMenuItems(): List<MenuItem> {
        val isAtFirstMenu = MenuManager.getInstance().menuHierarchy?.isAtFirstMenu() ?: true

        // Load user-added items if menuId is provided
        val userAddedItems = menuId?.let {
            MenuUserItemsHelper.loadUserAddedItems(it, accessibilityService)
        } ?: emptyList()

        val allItems = items + userAddedItems

        return if (!isAtFirstMenu && showNavMenuItems) {
            val previousMenuItem = MenuItem(
                id = "previous_menu_first",
                drawableId = R.drawable.ic_previous_menu,
                labelResource = R.string.menu_item_previous_menu,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().menuHierarchy?.popMenu() }
            )
            listOf(previousMenuItem) + allItems
        } else {
            allItems
        }
    }

    /**
     * Get the dynamic menu items
     * @return The dynamic menu items
     */
    suspend fun getDynamicMenuItems(): List<MenuItem>? {
        return dynamicLoad?.invoke()
    }


    /**
     * Determine whether to show navigation menu items
     * @return true if navigation menu items should be shown, false otherwise
     */
    fun shouldShowNavMenuItems(): Boolean {
        return showNavMenuItems
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