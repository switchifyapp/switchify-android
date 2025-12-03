package com.enaboapps.switchify.service.menu.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * Data Access Object for menu item configurations.
 * Provides methods to read and write menu customization data.
 */
@Dao
interface MenuItemConfigurationDao {

    /**
     * Get all menu item configurations for a specific menu.
     * Results are ordered by position ascending.
     *
     * @param menuId The ID of the menu
     * @return List of configurations for the menu, ordered by position
     */
    @Query("SELECT * FROM menu_item_configurations WHERE menu_id = :menuId ORDER BY position ASC")
    suspend fun getConfigurationsForMenu(menuId: String): List<MenuItemConfiguration>

    /**
     * Get configuration for a specific menu item.
     *
     * @param menuId The ID of the menu
     * @param itemId The ID of the menu item
     * @return The configuration if it exists, null otherwise
     */
    @Query("SELECT * FROM menu_item_configurations WHERE menu_id = :menuId AND item_id = :itemId LIMIT 1")
    suspend fun getConfiguration(menuId: String, itemId: String): MenuItemConfiguration?

    /**
     * Insert a new menu item configuration.
     * If a configuration with the same (menuId, itemId) exists, it will be replaced.
     *
     * @param configuration The configuration to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfiguration(configuration: MenuItemConfiguration)

    /**
     * Insert multiple configurations at once.
     *
     * @param configurations List of configurations to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigurations(configurations: List<MenuItemConfiguration>)

    /**
     * Update an existing configuration.
     *
     * @param configuration The configuration to update
     */
    @Update
    suspend fun updateConfiguration(configuration: MenuItemConfiguration)

    /**
     * Delete all configurations for a specific menu.
     * Useful for resetting a menu to defaults.
     *
     * @param menuId The ID of the menu
     */
    @Query("DELETE FROM menu_item_configurations WHERE menu_id = :menuId")
    suspend fun deleteConfigurationsForMenu(menuId: String)

    /**
     * Delete a specific menu item configuration.
     *
     * @param menuId The ID of the menu
     * @param itemId The ID of the menu item
     */
    @Query("DELETE FROM menu_item_configurations WHERE menu_id = :menuId AND item_id = :itemId")
    suspend fun deleteConfiguration(menuId: String, itemId: String)

    /**
     * Delete all menu item configurations from the database.
     * Useful for complete reset of all menus.
     */
    @Query("DELETE FROM menu_item_configurations")
    suspend fun deleteAllConfigurations()

    /**
     * Update positions for multiple items in a menu.
     * This is a transaction to ensure all updates happen atomically.
     *
     * @param menuId The ID of the menu
     * @param itemPositions Map of item IDs to their new positions
     */
    @Transaction
    suspend fun updatePositions(menuId: String, itemPositions: Map<String, Int>) {
        itemPositions.forEach { (itemId, position) ->
            val existing = getConfiguration(menuId, itemId)
            if (existing != null) {
                updateConfiguration(existing.copy(position = position))
            } else {
                insertConfiguration(
                    MenuItemConfiguration(
                        menuId = menuId,
                        itemId = itemId,
                        position = position,
                        isVisible = true
                    )
                )
            }
        }
    }

    /**
     * Check if any configurations exist for a menu.
     *
     * @param menuId The ID of the menu
     * @return True if configurations exist, false otherwise
     */
    @Query("SELECT COUNT(*) > 0 FROM menu_item_configurations WHERE menu_id = :menuId")
    suspend fun hasConfigurationsForMenu(menuId: String): Boolean
}
