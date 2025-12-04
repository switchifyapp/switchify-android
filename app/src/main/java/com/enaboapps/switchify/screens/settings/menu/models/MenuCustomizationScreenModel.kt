package com.enaboapps.switchify.screens.settings.menu.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemDefinition
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
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

class MenuCustomizationScreenModel(private val context: Context) : ViewModel() {

    private val repository = MenuConfigurationRepository(context)

    // Available menus with their display names
    val availableMenus = MutableStateFlow(
        listOf(
            "main_menu" to R.string.menu_title_main,
            "device_menu" to R.string.menu_title_device,
            "volume_control_menu" to R.string.action_volume_control,
            "gestures_menu" to R.string.menu_title_gestures,
            "tap_gestures_menu" to R.string.menu_title_tap,
            "swipe_gestures_menu" to R.string.menu_title_swipe,
            "pinch_gestures_menu" to R.string.menu_title_pinch,
            "scroll_menu" to R.string.menu_title_scroll,
            "media_control_menu" to R.string.menu_title_media,
            "edit_menu" to R.string.menu_title_edit
        )
    )

    private val _selectedMenuId = MutableStateFlow("main_menu")
    val selectedMenuId: StateFlow<String> = _selectedMenuId.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _visibilityMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val visibilityMap: StateFlow<Map<String, Boolean>> = _visibilityMap.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _paletteDialogVisible = MutableStateFlow(false)
    val paletteDialogVisible: StateFlow<Boolean> = _paletteDialogVisible.asStateFlow()

    private val _availablePaletteItems = MutableStateFlow<List<PaletteItem>>(emptyList())
    val availablePaletteItems: StateFlow<List<PaletteItem>> = _availablePaletteItems.asStateFlow()

    private var originalItems: List<MenuItem> = emptyList()
    private var originalVisibilityMap: Map<String, Boolean> = emptyMap()

    // Item IDs that are submenu links (not leaf action items)
    private val submenuLinkItemIds = setOf(
        // Main menu submenu links
        MenuConstants.ItemIds.Main.GESTURES,
        MenuConstants.ItemIds.Main.SCROLL,
        MenuConstants.ItemIds.Main.QUICK_APPS,
        MenuConstants.ItemIds.Main.GESTURE_PATTERNS,
        MenuConstants.ItemIds.Main.DEVICE,
        MenuConstants.ItemIds.Main.MEDIA_CONTROL,
        MenuConstants.ItemIds.Main.EDIT,
        // Gestures menu submenu links
        MenuConstants.ItemIds.Gestures.TAP_GESTURES,
        MenuConstants.ItemIds.Gestures.SWIPE_GESTURES,
        MenuConstants.ItemIds.Gestures.PINCH_GESTURES,
        MenuConstants.ItemIds.Gestures.FINGER_MODE,
        // Device and Media volume control links
        MenuConstants.ItemIds.Device.VOLUME_CONTROL,
        MenuConstants.ItemIds.Media.VOLUME_CONTROL
    )

    /**
     * Switches the selected menu to the given ID and reloads its items unless the menu is already selected or a save is in progress.
     *
     * @param menuId The identifier of the menu to select.
     */
    fun selectMenu(menuId: String) {
        if (_selectedMenuId.value != menuId && !_isSaving.value) {
            _selectedMenuId.value = menuId
            loadMenuItems()
        }
    }

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
            android.util.Log.d("MenuCustomizationModel", "loadMenuItems: Starting to load menu $menuId")

            // Get default items for the menu from code
            val defaultItems = getDefaultMenuItemsForMenu(menuId)
            android.util.Log.d("MenuCustomizationModel", "loadMenuItems: Got ${defaultItems.size} default items")

            // Filter out navigation items (small items and menu hierarchy manipulators)
            val filterableDefaultItems = defaultItems.filter {
                !it.isSmall && !it.isMenuHierarchyManipulator
            }
            android.util.Log.d("MenuCustomizationModel", "loadMenuItems: After filtering, ${filterableDefaultItems.size} default items")

            // Load user-added items if this is the main menu
            val userAddedItems = if (menuId == MenuConstants.MenuIds.MAIN_MENU) {
                val userConfigs = repository.getUserAddedItems(menuId)
                android.util.Log.d("MenuCustomizationModel", "loadMenuItems: Found ${userConfigs.size} user-added items")

                userConfigs.mapNotNull { config ->
                    val sourceMenuId = config.sourceMenuId ?: return@mapNotNull null
                    val definition = MenuItemRegistry.getDefinition(sourceMenuId, config.itemId)
                    if (definition == null) {
                        android.util.Log.w("MenuCustomizationModel", "loadMenuItems: Could not find definition for ${config.itemId} from $sourceMenuId")
                        return@mapNotNull null
                    }

                    MenuItem(
                        definition = definition,
                        action = {} // Empty action for customization UI
                    )
                }
            } else {
                emptyList()
            }
            android.util.Log.d("MenuCustomizationModel", "loadMenuItems: Created ${userAddedItems.size} user-added MenuItem instances")

            // Combine default and user-added items
            val filterableItems = filterableDefaultItems + userAddedItems
            android.util.Log.d("MenuCustomizationModel", "loadMenuItems: Total filterable items: ${filterableItems.size}")

            // Load configurations from database
            val configurations = repository.getMenuConfigurations(menuId)
            android.util.Log.d("MenuCustomizationModel", "loadMenuItems: Got ${configurations.size} configurations from database")

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
                android.util.Log.d("MenuCustomizationModel", "loadMenuItems: Menu changed during load, aborting")
                return@launch
            }

            // Store state
            android.util.Log.d("MenuCustomizationModel", "loadMenuItems: Setting menu items to ${orderedItems.size} items")
            _menuItems.value = orderedItems
            _visibilityMap.value = visibilityMap
            originalItems = orderedItems.toList()
            originalVisibilityMap = visibilityMap.toMap()
            _hasUnsavedChanges.value = false
            android.util.Log.d("MenuCustomizationModel", "loadMenuItems: Completed successfully")
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
     * Toggles the visibility state for the menu item identified by itemId.
     *
     * Updates the internal visibility map and triggers change detection so the unsaved-changes state is re-evaluated.
     *
     * @param itemId The identifier of the menu item whose visibility will be toggled.
     */
    fun toggleItemVisibility(itemId: String) {
        val currentVisibility = _visibilityMap.value[itemId] ?: true
        _visibilityMap.value = _visibilityMap.value.toMutableMap().apply {
            put(itemId, !currentVisibility)
        }
        checkForChanges()
    }

    /**
     * Moves a menu item within the current item list from one index to another.
     *
     * Updates the model's menu-items state and triggers change detection for unsaved changes.
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
            checkForChanges()
        }
    }

    /**
     * Updates the unsaved-changes flag based on whether the current items or visibility differ from the originals.
     *
     * Compares the current `_menuItems` and `_visibilityMap` with `originalItems` and `originalVisibilityMap`
     * and sets `_hasUnsavedChanges.value` to `true` if either differs, otherwise `false`.
     */
    private fun checkForChanges() {
        val itemsChanged = _menuItems.value != originalItems
        val visibilityChanged = _visibilityMap.value != originalVisibilityMap
        _hasUnsavedChanges.value = itemsChanged || visibilityChanged
    }

    /**
     * Persist the current menu item order and visibility to the repository and update internal change-tracking state.
     *
     * If there are no unsaved changes or a save is already in progress, the call returns immediately without performing work.
     * On successful save, the snapshot of original items and visibility is updated and the unsaved-changes flag is cleared.
     * The saving state flag is set while the save is in progress and cleared when it finishes.
     */
    fun saveChanges() {
        if (!_hasUnsavedChanges.value || _isSaving.value) return

        viewModelScope.launch {
            _isSaving.value = true

            try {
                // Snapshot current state to avoid races
                val menuId = _selectedMenuId.value
                val itemsSnapshot = _menuItems.value.toList()
                val visibilitySnapshot = _visibilityMap.value.toMap()

                // Save the snapshot
                repository.saveMenuItemOrder(
                    menuId = menuId,
                    items = itemsSnapshot,
                    visibilityMap = visibilitySnapshot
                )

                // Update original state to the saved snapshot
                originalItems = itemsSnapshot
                originalVisibilityMap = visibilitySnapshot

                // Recompute dirty flag against latest in-memory state
                checkForChanges()
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Resets the selected menu's user configurations to the repository defaults and reloads its items.
     *
     * While performing the reset this function sets the ViewModel's saving state so concurrent saves are avoided,
     * and restores the saving state when the operation completes.
     */
    fun resetToDefault() {
        viewModelScope.launch {
            _isSaving.value = true

            try {
                // Delete configurations for this menu
                repository.resetMenuToDefault(_selectedMenuId.value)

                // Reload items
                loadMenuItems()
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Opens the palette dialog and loads available items.
     * Only available for the main menu.
     */
    fun openPalette() {
        if (_selectedMenuId.value != MenuConstants.MenuIds.MAIN_MENU) return

        viewModelScope.launch {
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
     * Loads all available menu items from other menus, filtering out submenu links
     * and marking items that are already in the main menu.
     */
    private suspend fun loadAvailablePaletteItems() {
        val currentMainMenuItems = _menuItems.value.map { it.id }.toSet()
        val paletteItems = mutableListOf<PaletteItem>()

        // Define source menus and their names
        val sourceMenus = listOf(
            MenuConstants.MenuIds.DEVICE_MENU to R.string.menu_title_device,
            MenuConstants.MenuIds.VOLUME_CONTROL_MENU to R.string.action_volume_control,
            MenuConstants.MenuIds.GESTURES_MENU to R.string.menu_title_gestures,
            MenuConstants.MenuIds.TAP_GESTURES_MENU to R.string.menu_title_tap,
            MenuConstants.MenuIds.SWIPE_GESTURES_MENU to R.string.menu_title_swipe,
            MenuConstants.MenuIds.PINCH_GESTURES_MENU to R.string.menu_title_pinch,
            MenuConstants.MenuIds.SCROLL_MENU to R.string.menu_title_scroll,
            MenuConstants.MenuIds.MEDIA_CONTROL_MENU to R.string.menu_title_media,
            MenuConstants.MenuIds.EDIT_MENU to R.string.menu_title_edit
        )

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
                        isAlreadyAdded = currentMainMenuItems.contains(definition.id)
                    )
                )
            }
        }

        _availablePaletteItems.value = paletteItems
    }

    /**
     * Adds an item from another menu to the main menu.
     */
    fun addItemToMainMenu(sourceMenuId: String, itemId: String) {
        android.util.Log.d("MenuCustomizationModel", "addItemToMainMenu called: sourceMenuId=$sourceMenuId, itemId=$itemId")
        viewModelScope.launch {
            try {
                android.util.Log.d("MenuCustomizationModel", "Calling repository.addUserItemToMenu...")
                repository.addUserItemToMenu(
                    sourceMenuId = sourceMenuId,
                    itemId = itemId,
                    targetMenuId = MenuConstants.MenuIds.MAIN_MENU
                )
                android.util.Log.d("MenuCustomizationModel", "Successfully added item to repository")

                // Reload menu items to show the newly added item
                android.util.Log.d("MenuCustomizationModel", "Reloading menu items...")
                loadMenuItems()

                // Reload palette items to update "already added" status
                android.util.Log.d("MenuCustomizationModel", "Reloading palette items...")
                loadAvailablePaletteItems()

                android.util.Log.d("MenuCustomizationModel", "addItemToMainMenu completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("MenuCustomizationModel", "Error adding item to main menu", e)
            }
        }
    }

    /**
     * Removes a user-added item from the main menu.
     */
    fun removeUserItem(itemId: String) {
        viewModelScope.launch {
            repository.removeUserItem(MenuConstants.MenuIds.MAIN_MENU, itemId)

            // Reload menu items
            loadMenuItems()
        }
    }

    /**
     * Checks if an item is user-added (has a source menu) in the currently selected menu.
     */
    suspend fun isUserAddedItem(itemId: String): Boolean {
        return repository.isUserAddedItem(_selectedMenuId.value, itemId)
    }
}