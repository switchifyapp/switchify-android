package com.enaboapps.switchify.screens.settings.menu.models

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemDefinition
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuUserItemsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data class representing an item in the palette dialog.
 */
data class PaletteItem(
    val sourceMenuId: String,
    val sourceMenuName: Int,
    val itemId: String,
    val definition: MenuItemDefinition,
    val isAlreadyAdded: Boolean
)

class MenuCustomizationScreenModel(
    application: Application,
    initialMenuId: String
) : ViewModel() {

    private val repository = MenuConfigurationRepository(application)

    private val _selectedMenuId = MutableStateFlow(initialMenuId)
    val selectedMenuId: StateFlow<String> = _selectedMenuId.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _visibilityMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val visibilityMap: StateFlow<Map<String, Boolean>> = _visibilityMap.asStateFlow()

    private val _paletteDialogVisible = MutableStateFlow(false)
    val paletteDialogVisible: StateFlow<Boolean> = _paletteDialogVisible.asStateFlow()

    private val _availablePaletteItems = MutableStateFlow<List<PaletteItem>>(emptyList())
    val availablePaletteItems: StateFlow<List<PaletteItem>> = _availablePaletteItems.asStateFlow()

    private val _userAddedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val userAddedItemIds: StateFlow<Set<String>> = _userAddedItemIds.asStateFlow()

    private val _paletteFilter = MutableStateFlow<String?>(null)
    val paletteFilter: StateFlow<String?> = _paletteFilter.asStateFlow()

    private val _selectedItemId = MutableStateFlow<String?>(null)
    val selectedItemId: StateFlow<String?> = _selectedItemId.asStateFlow()


    // Item IDs that are submenu links (not leaf action items)
    private val submenuLinkItemIds = setOf(
        // Main menu submenu links
        MenuConstants.ItemIds.Main.GESTURES,
        MenuConstants.ItemIds.Main.SCROLL,
        MenuConstants.ItemIds.Main.FAVOURITE_APPS,
        MenuConstants.ItemIds.Main.GESTURE_PATTERNS,
        MenuConstants.ItemIds.Main.DEVICE,
        MenuConstants.ItemIds.Main.MEDIA_CONTROL,
        MenuConstants.ItemIds.Main.EDIT,
        // Gestures menu submenu links
        MenuConstants.ItemIds.Gestures.TAP_GESTURES,
        MenuConstants.ItemIds.Gestures.SWIPE_GESTURES,
        MenuConstants.ItemIds.Gestures.PINCH_GESTURES,
        MenuConstants.ItemIds.Gestures.FINGER_MODE,
        // Tap gestures menu submenu link
        MenuConstants.ItemIds.TapGestures.TAP_AND_HOLD,
        // Device and Media volume control links
        MenuConstants.ItemIds.Device.VOLUME_CONTROL,
        MenuConstants.ItemIds.Media.VOLUME_CONTROL
    )

    /**
     * Loads and initializes the configurable menu items and their visibility for the currently selected menu.
     *
     * Fetches default definitions and persisted user configurations, computes a deterministic item order and
     * a complete visibility map, updates the ViewModel's state (menu items, visibility snapshot and originals),
     * and clears the unsaved-changes flag.
     */
    fun loadMenuItems() {
        viewModelScope.launch {
            val menuId = _selectedMenuId.value

            // Get default items for the menu from code
            val defaultItems = getDefaultMenuItemsForMenu(menuId)

            val filterableDefaultItems = defaultItems.filter {
                !it.isMenuHierarchyManipulator
            }

            // Load user-added items
            val userConfigs = repository.getUserAddedItems(menuId)

            val userAddedItems = userConfigs.mapNotNull { config ->
                val sourceMenuId = config.sourceMenuId ?: return@mapNotNull null
                val definition = MenuItemRegistry.getDefinition(sourceMenuId, config.itemId)
                    ?: return@mapNotNull null

                MenuItem(
                    definition = definition,
                    action = {} // Empty action for customization UI
                )
            }

            // Combine default and user-added items
            val filterableItems = filterableDefaultItems + userAddedItems

            // Load configurations from database
            val configurations = repository.getMenuConfigurations(menuId)

            // Extract user-added item IDs for StateFlow
            val userAddedIds = configurations
                .filter { it.sourceMenuId != null }
                .map { it.itemId }
                .toSet()
            _userAddedItemIds.value = userAddedIds

            // Build visibility map
            val visibilityMap = configurations.associate {
                it.itemId to it.isVisible
            }.toMutableMap()

            // Ensure all items have a visibility entry
            filterableItems.forEach { item ->
                if (!visibilityMap.containsKey(item.id)) {
                    visibilityMap[item.id] = true
                }
            }

            // Order items based on configurations
            val orderedItems = if (configurations.isNotEmpty()) {
                val configMap = configurations.associateBy { it.itemId }
                val itemMap = filterableItems.associateBy { it.id }

                // Items with configurations first, sorted by position
                val configuredItems = configurations
                    .filter { itemMap.containsKey(it.itemId) }
                    .sortedBy { it.position }
                    .mapNotNull { itemMap[it.itemId] }

                // Then items without configurations
                val unconfiguredItems = filterableItems.filter {
                    !configMap.containsKey(it.id)
                }

                configuredItems + unconfiguredItems
            } else {
                filterableItems
            }

            // Check if the menu is still selected (guard against stale results)
            if (_selectedMenuId.value != menuId) {
                return@launch
            }

            // Store state
            _menuItems.value = orderedItems
            _visibilityMap.value = visibilityMap
        }
    }

    /**
     * Builds default MenuItem instances for the given menu by converting registry definitions into MenuItem objects with no-op actions used by the customization UI.
     *
     * @param menuId The identifier of the menu whose default items to retrieve.
     * @return A list of MenuItem instances corresponding to the menu's default definitions, each with an empty action.
     */
    private fun getDefaultMenuItemsForMenu(menuId: String): List<MenuItem> {
        // Get definitions from the shared registry and convert to MenuItem instances
        val definitions = MenuItemRegistry.getDefinitionsForMenu(menuId)
        return definitions.map { def ->
            MenuItem(
                definition = def,
                action = {} // Empty action for customization UI
            )
        }
    }

    /**
     * Check whether a menu item identified by `itemId` is visible.
     *
     * @param itemId The identifier of the menu item.
     * @return `true` if the item is visible, `false` otherwise.
     */
    fun isItemVisible(itemId: String): Boolean {
        return _visibilityMap.value[itemId] ?: true
    }

    /**
     * Toggles the visibility state for the menu item identified by itemId and saves immediately.
     *
     * @param itemId The identifier of the menu item whose visibility will be toggled.
     */
    fun toggleItemVisibility(itemId: String) {
        val currentVisibility = _visibilityMap.value[itemId] ?: true
        _visibilityMap.value = _visibilityMap.value.toMutableMap().apply {
            put(itemId, !currentVisibility)
        }
        saveCurrentState()
    }

    /**
     * Moves a menu item within the current item list from one index to another and saves immediately.
     *
     * If either index is out of range, the operation is a no-op.
     *
     * @param fromIndex Index of the item to move.
     * @param toIndex Destination index where the item should be placed.
     */
    fun moveItem(fromIndex: Int, toIndex: Int) {
        val currentItems = _menuItems.value.toMutableList()
        if (fromIndex in currentItems.indices && toIndex in currentItems.indices) {
            val item = currentItems.removeAt(fromIndex)
            currentItems.add(toIndex, item)
            _menuItems.value = currentItems
            saveCurrentState()
        }
    }

    /**
     * Pick the item up for the two-step "select then insert" reorder flow.
     * Tapping the same id again clears the selection.
     */
    fun selectForMove(itemId: String) {
        _selectedItemId.value = if (_selectedItemId.value == itemId) null else itemId
    }

    /**
     * Insert the currently selected item immediately before [targetItemId] and
     * clear the selection. Adjusts the destination index by one when moving
     * downward, since [moveItem] does not compensate for the removal.
     * No-op if nothing is selected, the target is unknown, or it equals the
     * selection.
     */
    fun insertSelectedBefore(targetItemId: String) {
        val selectedId = _selectedItemId.value ?: return
        if (selectedId == targetItemId) return
        val items = _menuItems.value
        val fromIndex = items.indexOfFirst { it.id == selectedId }
        val targetIndex = items.indexOfFirst { it.id == targetItemId }
        if (fromIndex < 0 || targetIndex < 0) return
        val toIndex = if (fromIndex < targetIndex) targetIndex - 1 else targetIndex
        moveItem(fromIndex, toIndex)
        _selectedItemId.value = null
    }

    /**
     * Insert the currently selected item at the end of the list and clear the
     * selection. No-op if nothing is selected.
     */
    fun insertSelectedAtEnd() {
        val selectedId = _selectedItemId.value ?: return
        val items = _menuItems.value
        val fromIndex = items.indexOfFirst { it.id == selectedId }
        if (fromIndex < 0) return
        moveItem(fromIndex, items.size - 1)
        _selectedItemId.value = null
    }

    /**
     * Cancel the active selection without moving anything.
     */
    fun cancelSelection() {
        _selectedItemId.value = null
    }

    /**
     * Saves the current menu item order and visibility to the repository.
     */
    private fun saveCurrentState() {
        val menuId = _selectedMenuId.value
        val itemsSnapshot = _menuItems.value.toList()
        val visibilitySnapshot = _visibilityMap.value.toMap()

        viewModelScope.launch {
            repository.saveMenuItemOrder(
                menuId = menuId,
                items = itemsSnapshot,
                visibilityMap = visibilitySnapshot
            )
        }
    }

    /**
     * Opens the palette dialog and loads available items.
     * Resets filter to "All" when opening.
     */
    fun openPalette() {
        viewModelScope.launch {
            _paletteFilter.value = null // Reset to "All"
            loadAvailablePaletteItems()
            _paletteDialogVisible.value = true
        }
    }

    /**
     * Closes the palette dialog.
     */
    fun closePalette() {
        _paletteDialogVisible.value = false
    }

    /**
     * Sets the filter for palette items.
     * @param menuId The menu ID to filter by, or null for "All"
     */
    fun setPaletteFilter(menuId: String?) {
        _paletteFilter.value = menuId
    }

    /**
     * Loads all available menu items from other menus, filtering out submenu links
     * and marking items that are already in the current menu.
     * Dynamically excludes items from the currently selected menu.
     */
    private suspend fun loadAvailablePaletteItems() {
        val currentMenuItems = _menuItems.value.map { it.id }.toSet()
        val currentMenuId = _selectedMenuId.value
        val paletteItems = mutableListOf<PaletteItem>()

        // Get all available menus and filter out the current menu
        val sourceMenus = MenuConstants.customizableMenus.filter { it.first != currentMenuId }

        for ((menuId, menuNameRes) in sourceMenus) {
            val definitions = MenuItemRegistry.getDefinitionsForMenu(menuId)

            for (definition in definitions) {
                // Skip submenu links
                if (submenuLinkItemIds.contains(definition.id)) continue

                paletteItems.add(
                    PaletteItem(
                        sourceMenuId = menuId,
                        sourceMenuName = menuNameRes,
                        itemId = definition.id,
                        definition = definition,
                        isAlreadyAdded = currentMenuItems.contains(definition.id)
                    )
                )
            }
        }

        _availablePaletteItems.value = paletteItems
    }

    /**
     * Adds an item from another menu to the currently selected menu.
     */
    fun addItemToMenu(sourceMenuId: String, itemId: String) {
        viewModelScope.launch {
            try {
                repository.addUserItemToMenu(
                    sourceMenuId = sourceMenuId,
                    itemId = itemId,
                    targetMenuId = _selectedMenuId.value
                )

                // Clear cache so service picks up changes
                MenuUserItemsHelper.clearCache(_selectedMenuId.value)

                // Reload menu items to show the newly added item
                loadMenuItems()

                // Reload palette items to update "already added" status
                loadAvailablePaletteItems()
            } catch (e: Exception) {
                // Silently handle error - could add proper error handling here
            }
        }
    }

    /**
     * Removes a user-added item from the currently selected menu.
     */
    fun removeUserItem(itemId: String) {
        viewModelScope.launch {
            repository.removeUserItem(_selectedMenuId.value, itemId)

            // Clear cache so service picks up changes
            MenuUserItemsHelper.clearCache(_selectedMenuId.value)

            // Reload menu items
            loadMenuItems()

            // Reload palette items to update availability
            if (_paletteDialogVisible.value) {
                loadAvailablePaletteItems()
            }
        }
    }

    /**
     * Checks if an item is user-added (has a source menu) in the currently selected menu.
     */
    suspend fun isUserAddedItem(itemId: String): Boolean {
        return repository.isUserAddedItem(_selectedMenuId.value, itemId)
    }
}
