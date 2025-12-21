package com.enaboapps.switchify.screens.settings.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.ReorderMode
import com.enaboapps.switchify.components.ReorderableList
import com.enaboapps.switchify.screens.settings.menu.models.MenuCustomizationScreenModel
import com.enaboapps.switchify.screens.settings.menu.models.PaletteItem
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuConstants

/**
 * Hosts the menu customization screen, wiring a screen model and rendering the content inside a BaseView.
 *
 * Creates and provides a MenuCustomizationScreenModel to MenuCustomizationContent and configures the
 * surrounding BaseView (title, navigation, scroll and padding).
 *
 * @param navController Navigation controller used to handle navigation from this screen.
 */
@Composable
fun MenuCustomizationScreen(navController: NavController) {
    val context = LocalContext.current
    val screenModel: MenuCustomizationScreenModel = viewModel {
        MenuCustomizationScreenModel(context.applicationContext as android.app.Application)
    }

    val selectedMenuId by screenModel.selectedMenuId.collectAsState()

    BaseView(
        titleResId = R.string.screen_title_menu_customization,
        navController = navController,
        enableScroll = false,
        padding = 0.dp,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { screenModel.openPalette() }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_menu_item)
                )
            }
        }
    ) {
        MenuCustomizationContent(screenModel)
    }
}

/**
 * Displays the menu customization UI bound to the given screen model.
 *
 * Observes the model's state flows, triggers an initial load of menu items on composition,
 * and renders a menu selector, informational text, and a scrollable list of menu items with
 * per-item visibility toggles.
 *
 * @param screenModel The screen model supplying state (menu items, selected menu, visibility map,
 *                    saving/unsaved flags) and actions (load, select, toggle visibility, reset, save).
 */
@Composable
fun MenuCustomizationContent(screenModel: MenuCustomizationScreenModel) {
    val menuItems by screenModel.menuItems.collectAsState()
    val selectedMenuId by screenModel.selectedMenuId.collectAsState()
    val availableMenus by screenModel.availableMenus.collectAsState()
    val visibilityMap by screenModel.visibilityMap.collectAsState()
    val paletteDialogVisible by screenModel.paletteDialogVisible.collectAsState()
    val availablePaletteItems by screenModel.availablePaletteItems.collectAsState()
    val userAddedItemIds by screenModel.userAddedItemIds.collectAsState()

    LaunchedEffect(Unit) {
        screenModel.loadMenuItems()
    }

    // Show palette dialog if visible
    if (paletteDialogVisible) {
        val selectedFilter by screenModel.selectedPaletteFilter.collectAsState()

        PaletteDialog(
            items = availablePaletteItems,
            selectedFilter = selectedFilter,
            availableMenus = availableMenus,
            currentMenuId = selectedMenuId,
            onDismiss = { screenModel.closePalette() },
            onAddItem = { sourceMenuId, itemId ->
                screenModel.addItemToMenu(sourceMenuId, itemId)
            },
            onFilterChange = { menuId ->
                screenModel.setPaletteFilter(menuId)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Menu selector dropdown
        MenuSelector(
            availableMenus = availableMenus,
            selectedMenuId = selectedMenuId,
            onMenuSelected = { screenModel.selectMenu(it) },
            enabled = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Info text
        Text(
            text = stringResource(R.string.menu_customization_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Menu items list with drag and drop or arrow buttons
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (menuItems.isEmpty()) {
                Text(
                    text = stringResource(R.string.menu_customization_no_items),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            } else {
                ReorderableList(
                    items = menuItems,
                    onMove = { from, to -> screenModel.moveItem(from, to) },
                    key = { it.id },
                    defaultMode = ReorderMode.DRAG,
                    modifier = Modifier.fillMaxSize()
                ) { item, isDragging, reorderControls ->
                    val isUserAdded = remember(item.id, userAddedItemIds) {
                        item.id in userAddedItemIds
                    }
                    MenuItemRow(
                        item = item,
                        isVisible = visibilityMap[item.id] ?: true,
                        onVisibilityToggle = { screenModel.toggleItemVisibility(item.id) },
                        onDelete = if (isUserAdded) {
                            { screenModel.removeUserItem(item.id) }
                        } else null,
                        isDragging = isDragging,
                        reorderControls = reorderControls
                    )
                }
            }
        }
    }
}

/**
 * Palette dialog showing available menu items that can be added to the currently selected menu.
 */
@Composable
fun PaletteDialog(
    items: List<PaletteItem>,
    selectedFilter: String?,
    availableMenus: List<Pair<String, Int>>,
    currentMenuId: String,
    onDismiss: () -> Unit,
    onAddItem: (sourceMenuId: String, itemId: String) -> Unit,
    onFilterChange: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.add_menu_items_title))
                Spacer(modifier = Modifier.height(8.dp))
                PaletteFilterDropdown(
                    availableMenus = availableMenus,
                    currentMenuId = currentMenuId,
                    selectedFilter = selectedFilter,
                    onFilterChange = onFilterChange
                )
            }
        },
        text = {
            // Filter items based on selected filter
            val filteredItems = if (selectedFilter != null) {
                items.filter { it.sourceMenuId == selectedFilter }
            } else {
                items
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (filteredItems.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_items_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    // Group items by source menu
                    val groupedItems = filteredItems.groupBy { it.sourceMenuId }

                    groupedItems.forEach { (sourceMenuId, menuItems) ->
                        // Menu section header
                        item {
                            Text(
                                text = stringResource(menuItems.first().sourceMenuName),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // Menu items
                        items(menuItems) { paletteItem ->
                            PaletteItemRow(
                                item = paletteItem,
                                onAddClick = {
                                    onAddItem(paletteItem.sourceMenuId, paletteItem.itemId)
                                    onDismiss()
                                }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_close))
            }
        }
    )
}

/**
 * Filter dropdown for the palette dialog.
 * Shows "All" option plus all menus except the current one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteFilterDropdown(
    availableMenus: List<Pair<String, Int>>,
    currentMenuId: String,
    selectedFilter: String?,
    onFilterChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Filter out the current menu from the list
    val filterableMenus = availableMenus.filter { it.first != currentMenuId }

    // Build display value
    val displayValue = if (selectedFilter == null) {
        stringResource(R.string.filter_all_menus)
    } else {
        filterableMenus.find { it.first == selectedFilter }?.second?.let {
            stringResource(it)
        } ?: stringResource(R.string.filter_all_menus)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.filter_by_menu)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // "All" option
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_all_menus)) },
                onClick = {
                    onFilterChange(null)
                    expanded = false
                },
                leadingIcon = if (selectedFilter == null) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )

            // Individual menu options
            filterableMenus.forEach { (menuId, nameResId) ->
                DropdownMenuItem(
                    text = { Text(stringResource(nameResId)) },
                    onClick = {
                        onFilterChange(menuId)
                        expanded = false
                    },
                    leadingIcon = if (selectedFilter == menuId) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

/**
 * Row displaying a single palette item with an "Add" button.
 */
@Composable
fun PaletteItemRow(
    item: PaletteItem,
    onAddClick: () -> Unit
) {
    val itemLabel = item.definition.labelResource?.let { stringResource(it) } ?: item.itemId

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = itemLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.isAlreadyAdded) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )

            if (item.isAlreadyAdded) {
                Text(
                    text = stringResource(R.string.already_added),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Button(
                    onClick = onAddClick,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(stringResource(R.string.button_add))
                }
            }
        }
    }
}

/**
 * Renders a dropdown selector to choose which menu (by id) is active for customization.
 *
 * Displays the localized name of the currently selected menu and presents all entries from
 * `availableMenus` in an exposed dropdown; selection invokes `onMenuSelected`.
 *
 * @param availableMenus List of pairs where the first element is the menu id and the second is a
 * resource id for the menu's display name.
 * @param selectedMenuId The id of the currently selected menu; its name will be shown in the field.
 * @param onMenuSelected Callback invoked with the selected menu id when the user picks an item.
 * @param enabled If false, the field and dropdown cannot be opened or changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSelector(
    availableMenus: List<Pair<String, Int>>,
    selectedMenuId: String,
    onMenuSelected: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = availableMenus.find { it.first == selectedMenuId }?.second?.let { stringResource(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.label_select_menu)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled),
            enabled = enabled
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableMenus.forEach { (menuId, nameResId) ->
                DropdownMenuItem(
                    text = { Text(stringResource(nameResId)) },
                    onClick = {
                        onMenuSelected(menuId)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Displays a single menu item row with its label, reorder controls, and visibility toggle or delete button.
 *
 * The displayed label is chosen in order: the item's `labelResource` (localized), `userProvidedText`, then the item's `id`.
 *
 * @param item The MenuItem to render.
 * @param isVisible `true` if the item is currently visible; affects visual styling and the toggle icon.
 * @param onVisibilityToggle Callback invoked when the visibility toggle is pressed.
 * @param onDelete Callback invoked when the delete button is pressed (for user-added items). If null, shows visibility toggle instead.
 * @param isDragging `true` if the item is currently being dragged; affects visual styling.
 * @param reorderControls Composable function that renders the reorder controls (drag handle or arrow buttons).
 */
@Composable
fun MenuItemRow(
    item: MenuItem,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    onDelete: (() -> Unit)? = null,
    isDragging: Boolean = false,
    reorderControls: @Composable () -> Unit = {}
) {
    val itemLabel = item.labelResource?.let { stringResource(it) } ?: item.userProvidedText ?: item.id

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = if (isDragging) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (isDragging) 8.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Reorder controls (drag handle or arrow buttons)
            reorderControls()

            // Item label
            Text(
                text = itemLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isVisible) FontWeight.Normal else FontWeight.Light,
                color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            // Delete button for user-added items, visibility toggle for default items
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.button_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(onClick = onVisibilityToggle) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (isVisible) {
                            stringResource(R.string.content_desc_hide_item)
                        } else {
                            stringResource(R.string.content_desc_show_item)
                        },
                        tint = if (isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}