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

    fun selectMenu(menuId: String) {
        if (_selectedMenuId.value != menuId && !_isSaving.value) {
            _selectedMenuId.value = menuId
            loadMenuItems()
        }
    }

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

    fun isItemVisible(itemId: String): Boolean {
        return _visibilityMap.value[itemId] ?: true
    }

    fun toggleItemVisibility(itemId: String) {
        val currentVisibility = _visibilityMap.value[itemId] ?: true
        _visibilityMap.value = _visibilityMap.value.toMutableMap().apply {
            put(itemId, !currentVisibility)
        }
        checkForChanges()
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val currentItems = _menuItems.value.toMutableList()
        if (fromIndex in currentItems.indices && toIndex in currentItems.indices) {
            val item = currentItems.removeAt(fromIndex)
            currentItems.add(toIndex, item)
            _menuItems.value = currentItems
            checkForChanges()
        }
    }

    private fun checkForChanges() {
        val itemsChanged = _menuItems.value != originalItems
        val visibilityChanged = _visibilityMap.value != originalVisibilityMap
        _hasUnsavedChanges.value = itemsChanged || visibilityChanged
    }

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
