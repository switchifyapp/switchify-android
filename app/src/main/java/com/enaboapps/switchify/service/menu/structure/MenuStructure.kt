package com.enaboapps.switchify.service.menu.structure

import android.content.Context
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MenuStructure(
    val id: String,
    private val items: List<MenuItem>,
    private val context: Context? = null,
    private val coroutineScope: CoroutineScope? = null
) {
    private var cachedOrderedItems: List<MenuItem>? = null

    init {
        // Preload customizations asynchronously to avoid blocking (optimization for reused instances)
        if (context != null && coroutineScope != null && DeviceLockObserver.isUserUnlocked(context)) {
            coroutineScope.launch(Dispatchers.IO) {
                val repository = MenuConfigurationRepository(context)
                val orderedItems = repository.getOrderedMenuItems(id, items)
                cachedOrderedItems = orderedItems
            }
        }
    }

    /**
     * Retrieve the menu items with any user customizations applied.
     *
     * Returns the configured ordering with hidden items removed. If no Context was supplied or the user is locked, the original code-defined items are returned. The result is cached until invalidateCache() is called.
     *
     * @return A list of MenuItem in the active order — customized and filtered when available, otherwise the default items.
     */
    fun getMenuItems(): List<MenuItem> {
        // If no context provided, return default items
        if (context == null) {
            return items
        }

        // If user is locked, return default items for security
        if (!DeviceLockObserver.isUserUnlocked(context)) {
            return items
        }

        // Return cached items if available
        cachedOrderedItems?.let { return it }

        // Cache not ready - load synchronously to ensure correct ordering
        return runBlocking(Dispatchers.IO) {
            val repository = MenuConfigurationRepository(context)
            val orderedItems = repository.getOrderedMenuItems(id, items)
            cachedOrderedItems = orderedItems
            orderedItems
        }
    }

    /**
     * Return the code-defined menu items without applying any user customizations.
     *
     * @return The original list of `MenuItem` objects defined in code.
     */
    fun getDefaultMenuItems(): List<MenuItem> {
        return items
    }

    /**
     * Retrieves the list of menu item ids in the current menu order, including any applied user customizations.
     *
     * @return A list of menu item id strings in the current order. 
     */
    fun getMenuItemIdList(): List<String> {
        return getMenuItems().map { it.id }
    }

    /**
     * Clears the cached customized menu ordering.
     */
    fun invalidateCache() {
        cachedOrderedItems = null
    }
}