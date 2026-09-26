package com.enaboapps.switchify.service.menu.structure

import android.content.Context
import com.enaboapps.switchify.service.menu.MenuItem
import kotlinx.coroutines.CoroutineScope

class MenuStructure(
    val id: String,
    private val items: List<MenuItem>,
    @Suppress("unused") private val context: Context? = null,
    @Suppress("unused") private val coroutineScope: CoroutineScope? = null
) {
    /**
     * Retrieve the code-defined menu items.
     *
     * User ordering and visibility customizations are applied asynchronously by
     * BaseMenu.getMenuItems(), so this never touches the database and is safe to
     * call on the main thread.
     *
     * @return The list of MenuItem defined in code.
     */
    fun getMenuItems(): List<MenuItem> = items

    /**
     * Return the code-defined menu items without applying any user customizations.
     *
     * @return The original list of `MenuItem` objects defined in code.
     */
    fun getDefaultMenuItems(): List<MenuItem> = items

    /**
     * Retrieves the list of menu item ids in code-defined order.
     *
     * @return A list of menu item id strings.
     */
    fun getMenuItemIdList(): List<String> = items.map { it.id }
}
