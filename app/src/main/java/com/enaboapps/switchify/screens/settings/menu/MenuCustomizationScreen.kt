package com.enaboapps.switchify.screens.settings.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
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
import com.enaboapps.switchify.screens.settings.menu.models.MenuCustomizationScreenModel
import com.enaboapps.switchify.screens.settings.menu.models.PaletteItem
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    val screenModel: MenuCustomizationScreenModel = viewModel { MenuCustomizationScreenModel(context) }

    BaseView(
        titleResId = R.string.screen_title_menu_customization,
        navController = navController,
        enableScroll = false,
        padding = 0.dp
    ) {
        MenuCustomizationContent(screenModel)
    }
}

/**
 * Displays the menu customization UI bound to the given screen model.
 *
 * Observes the model's state flows, triggers an initial load of menu items on composition,
 * and renders a menu selector, informational text, a scrollable list of menu items with
 * per-item visibility toggles, and Reset/Save action buttons that invoke the model's handlers.
 *
 * @param screenModel The screen model supplying state (menu items, selected menu, visibility map,
 *                    saving/unsaved flags) and actions (load, select, toggle visibility, reset, save).
 */
@Composable
fun MenuCustomizationContent(screenModel: MenuCustomizationScreenModel) {
    val menuItems by screenModel.menuItems.collectAsState()
    val selectedMenuId by screenModel.selectedMenuId.collectAsState()
    val availableMenus by screenModel.availableMenus.collectAsState()
    val hasUnsavedChanges by screenModel.hasUnsavedChanges.collectAsState()
    val isSaving by screenModel.isSaving.collectAsState()
    val visibilityMap by screenModel.visibilityMap.collectAsState()
    val paletteDialogVisible by screenModel.paletteDialogVisible.collectAsState()
    val availablePaletteItems by screenModel.availablePaletteItems.collectAsState()

    LaunchedEffect(Unit) {
        screenModel.loadMenuItems()
    }

    // Show palette dialog if visible
    if (paletteDialogVisible) {
        PaletteDialog(
            items = availablePaletteItems,
            onDismiss = {
                android.util.Log.d("MenuCustomScreen", "Palette dialog dismissed")
                screenModel.closePalette()
            },
            onAddItem = { sourceMenuId, itemId ->
                android.util.Log.d("MenuCustomScreen", "onAddItem callback: sourceMenuId=$sourceMenuId, itemId=$itemId")
                screenModel.addItemToMainMenu(sourceMenuId, itemId)
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (selectedMenuId == MenuConstants.MenuIds.MAIN_MENU && !isSaving) {
                FloatingActionButton(
                    onClick = { screenModel.openPalette() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_menu_item)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
        // Menu selector dropdown
        MenuSelector(
            availableMenus = availableMenus,
            selectedMenuId = selectedMenuId,
            onMenuSelected = { screenModel.selectMenu(it) },
            enabled = !isSaving
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Info text
        Text(
            text = stringResource(R.string.menu_customization_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Menu items list with drag and drop
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
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(
                    lazyListState = lazyListState,
                    onMove = { from, to ->
                        screenModel.moveItem(from.index, to.index)
                    }
                )

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(menuItems, key = { it.id }) { item ->
                        ReorderableItem(state = reorderableState, key = item.id) {
                            val isDragging = it
                            val isUserAdded = remember(item.id) {
                                kotlinx.coroutines.runBlocking {
                                    screenModel.isUserAddedItem(item.id)
                                }
                            }
                            MenuItemRow(
                                item = item,
                                isVisible = visibilityMap[item.id] ?: true,
                                onVisibilityToggle = { screenModel.toggleItemVisibility(item.id) },
                                onDelete = if (isUserAdded) {
                                    { screenModel.removeUserItem(item.id) }
                                } else null,
                                isDragging = isDragging,
                                dragHandle = {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = stringResource(R.string.content_desc_drag_handle),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { screenModel.resetToDefault() },
                modifier = Modifier.weight(1f),
                enabled = !isSaving
            ) {
                Text(stringResource(R.string.button_reset_to_default))
            }

            Button(
                onClick = { screenModel.saveChanges() },
                modifier = Modifier.weight(1f),
                enabled = hasUnsavedChanges && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.button_save))
                }
            }
        }
        }
    }
}

/**
 * Palette dialog showing available menu items that can be added to the main menu.
 */
@Composable
fun PaletteDialog(
    items: List<PaletteItem>,
    onDismiss: () -> Unit,
    onAddItem: (sourceMenuId: String, itemId: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_menu_items_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Group items by source menu
                val groupedItems = items.groupBy { it.sourceMenuId }

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
                                android.util.Log.d("MenuCustomScreen", "PaletteItemRow onAddClick: ${paletteItem.itemId}")
                                onAddItem(paletteItem.sourceMenuId, paletteItem.itemId)
                                onDismiss()
                            }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
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
                    onClick = {
                        android.util.Log.d("MenuCustomScreen", "Add button clicked for item: ${item.itemId}")
                        onAddClick()
                    },
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
                .menuAnchor(),
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
 * Displays a single menu item row with its label, drag handle, and visibility toggle or delete button.
 *
 * The displayed label is chosen in order: the item's `labelResource` (localized), `userProvidedText`, then the item's `id`.
 *
 * @param item The MenuItem to render.
 * @param isVisible `true` if the item is currently visible; affects visual styling and the toggle icon.
 * @param onVisibilityToggle Callback invoked when the visibility toggle is pressed.
 * @param onDelete Callback invoked when the delete button is pressed (for user-added items). If null, shows visibility toggle instead.
 * @param isDragging `true` if the item is currently being dragged; affects visual styling.
 * @param dragHandle Composable function that renders the drag handle for reordering.
 */
@Composable
fun MenuItemRow(
    item: MenuItem,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    onDelete: (() -> Unit)? = null,
    isDragging: Boolean = false,
    dragHandle: @Composable () -> Unit = {}
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
            // Drag handle
            dragHandle()

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