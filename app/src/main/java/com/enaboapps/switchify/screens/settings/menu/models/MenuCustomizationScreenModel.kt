package com.enaboapps.switchify.screens.settings.menu.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private var originalItems: List<MenuItem> = emptyList()
    private var originalVisibilityMap: Map<String, Boolean> = emptyMap()

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

            // Get default items for the menu from code
            val defaultItems = getDefaultMenuItemsForMenu(menuId)

            // Filter out navigation items (small items and menu hierarchy manipulators)
            val filterableItems = defaultItems.filter {
                !it.isSmall && !it.isMenuHierarchyManipulator
            }

            // Load configurations from database
            val configurations = repository.getMenuConfigurations(menuId)

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

            // Store state
            _menuItems.value = orderedItems
            _visibilityMap.value = visibilityMap
            originalItems = orderedItems.toList()
            originalVisibilityMap = visibilityMap.toMap()
            _hasUnsavedChanges.value = false
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
                // Save the current order and visibility
                repository.saveMenuItemOrder(
                    menuId = _selectedMenuId.value,
                    items = _menuItems.value,
                    visibilityMap = _visibilityMap.value
                )

                // Update original state
                originalItems = _menuItems.value.toList()
                originalVisibilityMap = _visibilityMap.value.toMap()

                _hasUnsavedChanges.value = false
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
}