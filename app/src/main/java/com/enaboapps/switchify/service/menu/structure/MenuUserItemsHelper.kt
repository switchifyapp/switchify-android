package com.enaboapps.switchify.service.menu.structure

import android.util.Log
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper object for loading user-added menu items across different menus.
 * Implements caching to minimize database queries and improve performance.
 */
object MenuUserItemsHelper {
    private const val TAG = "MenuUserItemsHelper"
    private const val LOAD_TIMEOUT_MS = 2000L // 2 second timeout for database queries

    // Cache for user-added items per menu to avoid repeated database queries
    // Key: menuId, Value: List of MenuItems
    private val cache = ConcurrentHashMap<String, List<MenuItem>>()

    /**
     * Clears the cache for a specific menu or all menus.
     * Should be called when user adds/removes items.
     *
     * @param menuId Optional menu ID to clear. If null, clears entire cache.
     */
    fun clearCache(menuId: String? = null) {
        if (menuId != null) {
            cache.remove(menuId)
            Log.d(TAG, "Cleared cache for menu: $menuId")
        } else {
            cache.clear()
            Log.d(TAG, "Cleared entire menu items cache")
        }
    }

    /**
     * Loads user-added items for a specific menu.
     * Uses MenuActionResolver to bind the appropriate actions for each item.
     * Results are cached to improve performance on subsequent calls.
     *
     * @param menuId The ID of the menu to load user-added items for
     * @param accessibilityService The accessibility service instance
     * @return List of MenuItem objects representing user-added items
     */
    suspend fun loadUserAddedItems(
        menuId: String,
        accessibilityService: SwitchifyAccessibilityService
    ): List<MenuItem> {
        // Return cached items if available
        cache[menuId]?.let {
            Log.d(TAG, "Returning cached items for menu: $menuId (${it.size} items)")
            return it
        }

        return try {
            val repository = MenuConfigurationRepository(accessibilityService)
            val coroutineScope = accessibilityService.getServiceScope()

            // Add timeout to prevent indefinite blocking
            val items = withContext(Dispatchers.IO) {
                withTimeout(LOAD_TIMEOUT_MS) {
                    val userAddedConfigs = repository.getUserAddedItems(menuId)

                    Log.d(TAG, "Loading ${userAddedConfigs.size} user-added items for menu: $menuId")

                    userAddedConfigs.mapNotNull { config ->
                        val sourceMenuId = config.sourceMenuId ?: return@mapNotNull null
                        val definition = MenuItemRegistry.getDefinition(sourceMenuId, config.itemId)

                        if (definition == null) {
                            Log.w(TAG, "No definition found for item: ${config.itemId} from menu: $sourceMenuId")
                            return@mapNotNull null
                        }

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
            }

            // Cache the loaded items
            cache[menuId] = items
            Log.d(TAG, "Cached ${items.size} items for menu: $menuId")

            items
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout loading user-added items for menu: $menuId", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load user-added items for menu: $menuId", e)
            emptyList()
        }
    }
}
