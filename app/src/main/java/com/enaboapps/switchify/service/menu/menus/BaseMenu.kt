package com.enaboapps.switchify.service.menu.menus

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.MenuView
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder
import com.enaboapps.switchify.service.menu.structure.MenuUserItemsHelper

/**
 * This class represents a base menu
 * @property accessibilityService The accessibility service
 * @property items The menu items
 * @property menuId The menu identifier for loading user-added items
 * @property dynamicLoad The suspend function that loads dynamic menu items
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
     * Get the menu items with automatic previous menu button when applicable.
     * Loads user-added items and merges them with static items in the correct order
     * based on saved configurations.
     *
     * @return The menu items with previous menu button prepended if not at first menu
     */
    suspend fun getMenuItems(): List<MenuItem> {
        val isAtFirstMenu = MenuManager.getInstance().menuHierarchy?.isAtFirstMenu() ?: true

        // Load user-added items if menuId is provided
        val userAddedItems = menuId?.let {
            MenuUserItemsHelper.loadUserAddedItems(it, accessibilityService)
        } ?: emptyList()

        // Merge static items with user-added items
        val allItems = items + userAddedItems

        // Order items based on saved configurations (if menuId is provided)
        val orderedItems = menuId?.let {
            orderItemsByConfiguration(it, allItems)
        } ?: allItems

        return if (!isAtFirstMenu && showNavMenuItems) {
            val previousMenuItem = MenuItem(
                id = "previous_menu_first",
                drawableId = R.drawable.ic_previous_menu,
                labelResource = R.string.menu_item_previous_menu,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().menuHierarchy?.popMenu() }
            )
            listOf(previousMenuItem) + orderedItems
        } else {
            orderedItems
        }
    }

    /**
     * Orders menu items based on saved configurations from the database.
     * Uses the same logic as MenuCustomizationScreenModel to ensure consistent ordering.
     *
     * @param menuId The menu identifier
     * @param items The items to order
     * @return Items ordered by their saved positions
     */
    private suspend fun orderItemsByConfiguration(
        menuId: String,
        items: List<MenuItem>
    ): List<MenuItem> {
        val repository = MenuConfigurationRepository(accessibilityService)
        val configurations = repository.getMenuConfigurations(menuId)

        return if (configurations.isNotEmpty()) {
            val configMap = configurations.associateBy { it.itemId }
            val itemMap = items.associateBy { it.id }

            // Items with configurations first, sorted by position
            val configuredItems = configurations
                .filter { itemMap.containsKey(it.itemId) && (it.isVisible) }
                .sortedBy { it.position }
                .mapNotNull { itemMap[it.itemId] }

            // Then items without configurations
            val unconfiguredItems = items.filter {
                !configMap.containsKey(it.id)
            }

            configuredItems + unconfiguredItems
        } else {
            items
        }
    }

    /**
     * Get the dynamic menu items (for truly dynamic content like gesture patterns).
     * User-added items are now loaded in getMenuItems() instead.
     *
     * @return The dynamic menu items, or null if none
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