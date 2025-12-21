package com.enaboapps.switchify.service.menu.structure

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Helper object for loading user-added menu items across different menus.
 */
object MenuUserItemsHelper {

    /**
     * Loads user-added items for a specific menu.
     * Uses MenuActionResolver to bind the appropriate actions for each item.
     *
     * @param menuId The ID of the menu to load user-added items for
     * @param accessibilityService The accessibility service instance
     * @param coroutineScope The coroutine scope for async operations
     * @return List of MenuItem objects representing user-added items
     */
    fun loadUserAddedItems(
        menuId: String,
        accessibilityService: SwitchifyAccessibilityService,
        coroutineScope: CoroutineScope
    ): List<MenuItem> {
        return try {
            val repository = MenuConfigurationRepository(accessibilityService)

            kotlinx.coroutines.runBlocking {
                val userAddedConfigs = repository.getUserAddedItems(menuId)

                userAddedConfigs.mapNotNull { config ->
                    val sourceMenuId = config.sourceMenuId ?: return@mapNotNull null
                    val definition = MenuItemRegistry.getDefinition(sourceMenuId, config.itemId)
                        ?: return@mapNotNull null

                    val action = MenuActionResolver.resolveAction(
                        sourceMenuId = sourceMenuId,
                        itemId = config.itemId,
                        accessibilityService = accessibilityService,
                        coroutineScope = coroutineScope
                    )

                    MenuItem(
                        definition = definition,
                        action = action
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
