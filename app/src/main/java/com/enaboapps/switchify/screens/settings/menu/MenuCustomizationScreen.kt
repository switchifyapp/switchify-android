package com.enaboapps.switchify.screens.settings.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.enaboapps.switchify.components.SelectModeState
import com.enaboapps.switchify.screens.settings.menu.models.MenuCustomizationScreenModel
import com.enaboapps.switchify.screens.settings.menu.models.PaletteItem
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuSizeManager

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
    val context = LocalContext.current
    val menuItems by screenModel.menuItems.collectAsState()
    val selectedMenuId by screenModel.selectedMenuId.collectAsState()
    val availableMenus by screenModel.availableMenus.collectAsState()
    val visibilityMap by screenModel.visibilityMap.collectAsState()
    val paletteDialogVisible by screenModel.paletteDialogVisible.collectAsState()
    val availablePaletteItems by screenModel.availablePaletteItems.collectAsState()
    val userAddedItemIds by screenModel.userAddedItemIds.collectAsState()
    val paletteFilter by screenModel.paletteFilter.collectAsState()
    val selectedItemId by screenModel.selectedItemId.collectAsState()

    // Resolves a MenuItem to its display label using the same fallback chain as
    // MenuItemRow. Captured here so the SelectModeState callbacks can pass labels
    // into accessibility content descriptions from non-Composable scope.
    val itemLabelOf: (MenuItem) -> String = remember(context) {
        { item ->
            item.labelResource?.let { context.getString(it) }
                ?: item.userProvidedText
                ?: item.id
        }
    }

    LaunchedEffect(Unit) {
        screenModel.loadMenuItems()
    }

    // Show palette dialog if visible
    if (paletteDialogVisible) {
        PaletteDialog(
            items = availablePaletteItems,
            availableMenus = availableMenus,
            selectedFilter = paletteFilter,
            onFilterChange = { screenModel.setPaletteFilter(it) },
            onDismiss = { screenModel.closePalette() },
            onAddItem = { sourceMenuId, itemId ->
                screenModel.addItemToMenu(sourceMenuId, itemId)
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

        // Banner shown while a Select-mode selection is active. Placed above the
        // list so it stays visible while the user scrolls to pick a destination.
        val selectedItem = menuItems.firstOrNull { it.id == selectedItemId }
        if (selectedItem != null) {
            Spacer(modifier = Modifier.height(16.dp))
            SelectModeBanner(
                selectedLabel = itemLabelOf(selectedItem),
                onCancel = { screenModel.cancelSelection() }
            )
        }

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
                // Compute the visible-item index of each row so we can mark ring
                // boundaries: the service menu lays out ringSize items per ring
                // clockwise from the top, then paginates. ringSize comes from
                // the radial sizing profile (4 on phones, 6 on tablets, 8 on
                // large tablets). Hidden items are skipped when numbering
                // rings — a user who hides a few items sees the headers shift
                // to match what actually renders.
                val ringSize = MenuSizeManager.getRadialItemSize(context).itemsPerRing
                val visibleIndexById = remember(menuItems, visibilityMap) {
                    var idx = 0
                    buildMap {
                        menuItems.forEach { item ->
                            val visible = visibilityMap[item.id] ?: true
                            if (visible) {
                                put(item.id, idx)
                                idx++
                            }
                        }
                    }
                }
                val totalVisible = visibleIndexById.size
                val showRingHeaders = totalVisible > ringSize

                val selectModeState = remember(menuItems, selectedItemId, itemLabelOf) {
                    SelectModeState<MenuItem>(
                        selectedKey = selectedItemId,
                        getLabel = itemLabelOf,
                        onPickUp = { screenModel.selectForMove(it.id) },
                        onCancel = { screenModel.cancelSelection() },
                        onInsertBefore = { screenModel.insertSelectedBefore(it.id) },
                        onInsertAtEnd = { screenModel.insertSelectedAtEnd() }
                    )
                }
                val isSelectionActive = selectedItemId != null

                ReorderableList(
                    items = menuItems,
                    onMove = { from, to -> screenModel.moveItem(from, to) },
                    key = { it.id },
                    defaultMode = ReorderMode.DRAG,
                    selectModeState = selectModeState,
                    modifier = Modifier.fillMaxSize()
                ) { item, isDragging, reorderControls ->
                    val isUserAdded = remember(item.id, userAddedItemIds) {
                        item.id in userAddedItemIds
                    }
                    val visibleIndex = visibleIndexById[item.id]
                    val startsNewRing = showRingHeaders &&
                        visibleIndex != null && visibleIndex % ringSize == 0
                    val ringNumber = (visibleIndex ?: 0) / ringSize + 1
                    val isThisRowSelected = isSelectionActive && item.id == selectedItemId
                    val isOtherRowSelected = isSelectionActive && !isThisRowSelected
                    val onRowClick: (() -> Unit)? = if (isOtherRowSelected) {
                        { screenModel.insertSelectedBefore(item.id) }
                    } else null

                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (startsNewRing) {
                            Text(
                                text = stringResource(
                                    R.string.menu_customization_ring_header,
                                    ringNumber
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(
                                    top = 16.dp,
                                    bottom = 4.dp,
                                    start = 4.dp
                                )
                            )
                        }
                        MenuItemRow(
                            item = item,
                            isVisible = visibilityMap[item.id] ?: true,
                            onVisibilityToggle = { screenModel.toggleItemVisibility(item.id) },
                            onDelete = if (isUserAdded) {
                                { screenModel.removeUserItem(item.id) }
                            } else null,
                            isDragging = isDragging,
                            isHighlighted = isThisRowSelected,
                            showTrailingActions = !isSelectionActive,
                            onRowClick = onRowClick,
                            reorderControls = reorderControls
                        )
                    }
                }
            }
        }
    }
}

/**
 * Palette dialog showing available menu items that can be added to the current menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteDialog(
    items: List<PaletteItem>,
    availableMenus: List<Pair<String, Int>>,
    selectedFilter: String?,
    onFilterChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    onAddItem: (sourceMenuId: String, itemId: String) -> Unit
) {
    // Filter items based on selected filter
    val filteredItems = if (selectedFilter == null) {
        items
    } else {
        items.filter { it.sourceMenuId == selectedFilter }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_menu_items_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Filter dropdown
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedFilter == null) {
                            stringResource(R.string.filter_all_menus)
                        } else {
                            availableMenus.find { it.first == selectedFilter }?.second?.let { stringResource(it) } ?: ""
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.filter_by_menu)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        // "All menus" option
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.filter_all_menus)) },
                            onClick = {
                                onFilterChange(null)
                                expanded = false
                            }
                        )

                        // Individual menu options
                        availableMenus.forEach { (menuId, nameResId) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(nameResId)) },
                                onClick = {
                                    onFilterChange(menuId)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Items list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Group items by source menu
                    val groupedItems = filteredItems.groupBy { it.sourceMenuId }

                    groupedItems.forEach { (sourceMenuId, menuItems) ->
                        // Menu section header
                        item(key = "header_$sourceMenuId") {
                            Text(
                                text = stringResource(menuItems.first().sourceMenuName),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // Menu items
                        items(
                            items = menuItems,
                            key = { paletteItem -> "${paletteItem.sourceMenuId}_${paletteItem.itemId}" }
                        ) { paletteItem ->
                            PaletteItemRow(
                                item = paletteItem,
                                onAddClick = {
                                    onAddItem(paletteItem.sourceMenuId, paletteItem.itemId)
                                    onDismiss()
                                }
                            )
                        }

                        item(key = "spacer_$sourceMenuId") { Spacer(modifier = Modifier.height(8.dp)) }
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
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
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
    isHighlighted: Boolean = false,
    showTrailingActions: Boolean = true,
    onRowClick: (() -> Unit)? = null,
    reorderControls: @Composable () -> Unit = {}
) {
    val itemLabel = item.labelResource?.let { stringResource(it) } ?: item.userProvidedText ?: item.id

    val baseModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
    val rowModifier = if (onRowClick != null) {
        baseModifier.clickable(onClick = onRowClick)
    } else {
        baseModifier
    }

    Surface(
        modifier = rowModifier,
        color = when {
            isDragging -> MaterialTheme.colorScheme.primaryContainer
            isHighlighted -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
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
            // Reorder controls (drag handle, arrow buttons, or select control)
            reorderControls()

            // Item label
            Text(
                text = itemLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isVisible) FontWeight.Normal else FontWeight.Light,
                color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            if (showTrailingActions) {
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
}

@Composable
private fun SelectModeBanner(
    selectedLabel: String,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.menu_customization_select_hint,
                    selectedLabel
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.menu_customization_select_cancel_button))
            }
        }
    }
}