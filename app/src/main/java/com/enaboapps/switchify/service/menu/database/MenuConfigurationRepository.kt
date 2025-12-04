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
         * Get the menu items ordered and filtered according to saved user customizations for the specified menu.
         *
         * Applies saved item order and visibility; items without a saved configuration are appended in their original order.
         *
         * @param menuId The identifier of the menu whose configurations are applied.
         * @param defaultItems The default menu items to merge with stored configurations.
         * @return A list of menu items reflecting saved order and visibility, with unconfigured default items appended.
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
     * Persist the order and visibility of menu items for the specified menu.
     *
     * @param menuId The identifier of the menu to update.
     * @param items The menu items in the desired order; their list index defines stored positions.
     * @param visibilityMap Optional map from item IDs to visibility; items absent from the map (or when the map is `null`) are stored as visible.
     */
    suspend fun saveMenuItemOrder(
        menuId: String,
        items: List<MenuItem>,
        visibilityMap: Map<String, Boolean>? = null
    ) = withContext(Dispatchers.IO) {
        // Get existing configurations to preserve sourceMenuId
        val existingConfigs = dao.getConfigurationsForMenu(menuId).associateBy { it.itemId }

        val configurations = items.mapIndexed { index, item ->
            MenuItemConfiguration(
                menuId = menuId,
                itemId = item.id,
                position = index,
                isVisible = visibilityMap?.get(item.id) ?: true,
                sourceMenuId = existingConfigs[item.id]?.sourceMenuId
            )
        }
        dao.insertConfigurations(configurations)
    }

    /**
         * Set visibility for a specific menu item, creating a configuration with a default position if none exists.
         *
         * Updates the item's stored visibility when a configuration exists; otherwise inserts a new configuration
         * with position 0 and the given visibility.
         *
         * @param menuId Identifier of the menu containing the item.
         * @param itemId Identifier of the menu item to update.
         * @param isVisible `true` to mark the item visible, `false` to mark it hidden.
         */
    suspend fun setItemVisibility(menuId: String, itemId: String, isVisible: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = dao.getConfiguration(menuId, itemId)
            if (existing != null) {
                dao.updateConfiguration(existing.copy(isVisible = isVisible))
            } else {
                // If no configuration exists, create one with position at end
                val existingCount = dao.getConfigurationsForMenu(menuId).size
                dao.insertConfiguration(
                    MenuItemConfiguration(
                        menuId = menuId,
                        itemId = itemId,
                        position = existingCount,
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
     * Reverts all menus to their default configurations by removing all persisted menu item configurations.
     */
    suspend fun resetAllMenus() = withContext(Dispatchers.IO) {
        dao.deleteAllConfigurations()
    }

    /**
     * Determines whether the specified menu has any user customizations.
     *
     * @param menuId The menu identifier to check.
     * @return `true` if the menu has at least one customization, `false` otherwise.
     */
    suspend fun hasCustomizations(menuId: String): Boolean = withContext(Dispatchers.IO) {
        dao.hasConfigurationsForMenu(menuId)
    }

    /**
     * Retrieves the stored configuration for a specific menu item.
     *
     * @param menuId ID of the menu containing the item.
     * @param itemId ID of the menu item.
     * @return The MenuItemConfiguration if present, `null` otherwise.
     */
    suspend fun getItemConfiguration(
        menuId: String,
        itemId: String
    ): MenuItemConfiguration? = withContext(Dispatchers.IO) {
        dao.getConfiguration(menuId, itemId)
    }

    /**
         * Retrieves all item configurations for the specified menu.
         *
         * @param menuId The ID of the menu whose configurations should be returned.
         * @return A list of MenuItemConfiguration objects ordered by position.
         */
    suspend fun getMenuConfigurations(menuId: String): List<MenuItemConfiguration> =
        withContext(Dispatchers.IO) {
            dao.getConfigurationsForMenu(menuId)
        }

    /**
     * Adds a user-added item to the main menu from another source menu.
     *
     * @param sourceMenuId The ID of the source menu where the item originates
     * @param itemId The ID of the menu item to add
     * @param targetMenuId The ID of the target menu (typically main_menu)
     */
    suspend fun addUserItemToMenu(
        sourceMenuId: String,
        itemId: String,
        targetMenuId: String
    ) = withContext(Dispatchers.IO) {
        // Get current items count to place new item at the end
        val existingCount = dao.getConfigurationsForMenu(targetMenuId).size

        dao.insertConfiguration(
            MenuItemConfiguration(
                menuId = targetMenuId,
                itemId = itemId,
                position = existingCount,
                isVisible = true,
                sourceMenuId = sourceMenuId
            )
        )
    }

    /**
     * Removes a user-added item from the menu.
     * Only removes items that have a source_menu_id (user-added items).
     *
     * @param menuId The ID of the menu containing the item
     * @param itemId The ID of the item to remove
     */
    suspend fun removeUserItem(menuId: String, itemId: String) = withContext(Dispatchers.IO) {
        val config = dao.getConfiguration(menuId, itemId)
        if (config?.sourceMenuId != null) {
            dao.deleteConfiguration(menuId, itemId)
        }
    }

    /**
     * Retrieves all user-added items for the specified menu.
     *
     * @param menuId The ID of the menu
     * @return A list of MenuItemConfiguration objects where sourceMenuId is not null
     */
    suspend fun getUserAddedItems(menuId: String): List<MenuItemConfiguration> =
        withContext(Dispatchers.IO) {
            dao.getConfigurationsForMenu(menuId).filter { it.sourceMenuId != null }
        }

    /**
     * Checks if a menu item is user-added (has a source menu).
     *
     * @param menuId The ID of the menu
     * @param itemId The ID of the item
     * @return true if the item has a sourceMenuId, false otherwise
     */
    suspend fun isUserAddedItem(menuId: String, itemId: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.getConfiguration(menuId, itemId)?.sourceMenuId != null
        }
}