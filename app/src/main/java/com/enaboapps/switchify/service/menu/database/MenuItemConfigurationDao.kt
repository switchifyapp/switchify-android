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
     * Inserts a menu item configuration.
     *
     * If a configuration with the same `menuId` and `itemId` already exists, it is replaced.
     *
     * @param configuration The configuration to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfiguration(configuration: MenuItemConfiguration)

    /**
     * Inserts multiple menu item configurations into the database, replacing any existing entries with the same primary keys.
     *
     * @param configurations the configurations to insert; entries with matching primary keys will be replaced
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
     * Deletes all menu item configurations for the specified menu.
     *
     * @param menuId The ID of the menu.
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
     * Removes all rows from the menu_item_configurations table.
     */
    @Query("DELETE FROM menu_item_configurations")
    suspend fun deleteAllConfigurations()

    /**
     * Atomically update positions for multiple items within a menu.
     *
     * For each entry in `itemPositions`, updates the existing configuration's position if present;
     * otherwise inserts a new configuration with the provided position and `isVisible = true`.
     * This operation is executed within a database transaction so all changes are applied atomically.
     *
     * @param menuId The ID of the menu whose item positions will be updated.
     * @param itemPositions Map from item ID to the new position for that item.
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
     * Determines whether the given menu has any stored configurations.
     *
     * @param menuId The menu's identifier.
     * @return `true` if one or more configurations exist for the menu, `false` otherwise.
     */
    @Query("SELECT COUNT(*) > 0 FROM menu_item_configurations WHERE menu_id = :menuId")
    suspend fun hasConfigurationsForMenu(menuId: String): Boolean
}