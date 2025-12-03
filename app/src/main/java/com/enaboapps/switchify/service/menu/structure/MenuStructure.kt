package com.enaboapps.switchify.service.menu.structure

import android.content.Context
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import kotlinx.coroutines.runBlocking

class MenuStructure(
    val id: String,
    private val items: List<MenuItem>,
    private val context: Context? = null
) {
    private var cachedOrderedItems: List<MenuItem>? = null

    /**
     * Get menu items with user customizations applied.
     * Returns items in custom order with hidden items filtered out.
     * If no customizations exist or context is not provided, returns default items.
     */
    fun getMenuItems(): List<MenuItem> {
        // Return cached items if available
        if (cachedOrderedItems != null) {
            return cachedOrderedItems!!
        }

        // If no context provided, return default items
        if (context == null) {
            return items
        }

        // Load customized items from repository
        val repository = MenuConfigurationRepository(context)
        val orderedItems = runBlocking {
            repository.getOrderedMenuItems(id, items)
        }

        // Cache the result
        cachedOrderedItems = orderedItems
        return orderedItems
    }

    /**
     * Get the default (code-defined) menu items without customizations.
     */
    fun getDefaultMenuItems(): List<MenuItem> {
        return items
    }

    fun getMenuItemIdList(): List<String> {
        return getMenuItems().map { it.id }
    }

    /**
     * Clear the cached ordered items.
     * Call this when menu customizations are updated.
     */
    fun invalidateCache() {
        cachedOrderedItems = null
    }
}