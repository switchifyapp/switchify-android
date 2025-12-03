package com.enaboapps.switchify.service.menu.database

import android.content.Context
import com.enaboapps.switchify.service.menu.MenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for menu configuration data.
 * Provides a clean API for accessing and modifying menu item configurations.
 * Merges code-defined default menu structures with user customizations from the database.
 */
class MenuConfigurationRepository(context: Context) {

    private val database = MenuDatabase.getInstance(context)
    private val dao = database.menuItemConfigurationDao()

    /**
     * Get the ordered and filtered list of menu items for a menu.
     * Applies user customizations (order and visibility) from the database.
     * If no customizations exist, returns items in their original order.
     *
     * @param menuId The ID of the menu
     * @param defaultItems The default list of menu items from code
     * @return List of menu items with customizations applied
     */
    suspend fun getOrderedMenuItems(menuId: String, defaultItems: List<MenuItem>): List<MenuItem> =
        withContext(Dispatchers.IO) {
            val configurations = dao.getConfigurationsForMenu(menuId)

            // If no configurations exist, return default items
            if (configurations.isEmpty()) {
                return@withContext defaultItems
            }

            // Create a map of item IDs to their configurations
            val configMap = configurations.associateBy { it.itemId }

            // Create a map of item IDs to menu items for quick lookup
            val itemMap = defaultItems.associateBy { it.id }

            // Build the ordered list based on configurations
            val orderedItems = mutableListOf<MenuItem>()

            // First, add items that have configurations in their specified order
            configurations
                .filter { it.isVisible }
                .sortedBy { it.position }
                .forEach { config ->
                    itemMap[config.itemId]?.let { orderedItems.add(it) }
                }

            // Then, add any new items that don't have configurations yet (at the end)
            defaultItems.forEach { item ->
                if (!configMap.containsKey(item.id)) {
                    orderedItems.add(item)
                }
            }

            return@withContext orderedItems
        }

    /**
     * Save the order and visibility of menu items.
     *
     * @param menuId The ID of the menu
     * @param items List of menu items in their desired order
     * @param visibilityMap Map of item IDs to visibility states (null means visible)
     */
    suspend fun saveMenuItemOrder(
        menuId: String,
        items: List<MenuItem>,
        visibilityMap: Map<String, Boolean>? = null
    ) = withContext(Dispatchers.IO) {
        val configurations = items.mapIndexed { index, item ->
            MenuItemConfiguration(
                menuId = menuId,
                itemId = item.id,
                position = index,
                isVisible = visibilityMap?.get(item.id) ?: true
            )
        }
        dao.insertConfigurations(configurations)
    }

    /**
     * Update visibility of a specific menu item.
     *
     * @param menuId The ID of the menu
     * @param itemId The ID of the menu item
     * @param isVisible Whether the item should be visible
     */
    suspend fun setItemVisibility(menuId: String, itemId: String, isVisible: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = dao.getConfiguration(menuId, itemId)
            if (existing != null) {
                dao.updateConfiguration(existing.copy(isVisible = isVisible))
            } else {
                // If no configuration exists, create one with default position
                dao.insertConfiguration(
                    MenuItemConfiguration(
                        menuId = menuId,
                        itemId = itemId,
                        position = 0,
                        isVisible = isVisible
                    )
                )
            }
        }

    /**
     * Reset a menu to its default configuration by deleting all customizations.
     *
     * @param menuId The ID of the menu to reset
     */
    suspend fun resetMenuToDefault(menuId: String) = withContext(Dispatchers.IO) {
        dao.deleteConfigurationsForMenu(menuId)
    }

    /**
     * Reset all menus to their default configurations.
     */
    suspend fun resetAllMenus() = withContext(Dispatchers.IO) {
        dao.deleteAllConfigurations()
    }

    /**
     * Check if a menu has any customizations.
     *
     * @param menuId The ID of the menu
     * @return True if the menu has been customized, false otherwise
     */
    suspend fun hasCustomizations(menuId: String): Boolean = withContext(Dispatchers.IO) {
        dao.hasConfigurationsForMenu(menuId)
    }

    /**
     * Get the configuration for a specific menu item.
     *
     * @param menuId The ID of the menu
     * @param itemId The ID of the menu item
     * @return The configuration if it exists, null otherwise
     */
    suspend fun getItemConfiguration(
        menuId: String,
        itemId: String
    ): MenuItemConfiguration? = withContext(Dispatchers.IO) {
        dao.getConfiguration(menuId, itemId)
    }

    /**
     * Get all configurations for a menu.
     *
     * @param menuId The ID of the menu
     * @return List of configurations ordered by position
     */
    suspend fun getMenuConfigurations(menuId: String): List<MenuItemConfiguration> =
        withContext(Dispatchers.IO) {
            dao.getConfigurationsForMenu(menuId)
        }
}
