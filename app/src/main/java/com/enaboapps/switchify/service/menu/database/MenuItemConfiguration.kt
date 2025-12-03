package com.enaboapps.switchify.service.menu.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Entity representing a menu item's customization configuration.
 * Stores the position and visibility state for menu items within specific menus.
 *
 * @property menuId The ID of the menu this item belongs to (e.g., "main_menu", "device_menu")
 * @property itemId The ID of the menu item (from MenuItem.id)
 * @property position The custom position/order of the item within the menu (0-indexed)
 * @property isVisible Whether the menu item should be shown or hidden
 */
@Entity(
    tableName = "menu_item_configurations",
    primaryKeys = ["menu_id", "item_id"]
)
data class MenuItemConfiguration(
    @ColumnInfo(name = "menu_id")
    val menuId: String,

    @ColumnInfo(name = "item_id")
    val itemId: String,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "is_visible")
    val isVisible: Boolean = true
)
