package com.enaboapps.switchify.screens.settings.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.enaboapps.switchify.service.menu.MenuItem

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

    LaunchedEffect(Unit) {
        screenModel.loadMenuItems()
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

        // Menu items list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (menuItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.menu_customization_no_items),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                } else {
                    menuItems.forEach { item ->
                        MenuItemRow(
                            item = item,
                            isVisible = visibilityMap[item.id] ?: true,
                            onVisibilityToggle = { screenModel.toggleItemVisibility(item.id) }
                        )
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
 * Displays a single menu item row with its label and a visibility toggle.
 *
 * The displayed label is chosen in order: the item's `labelResource` (localized), `userProvidedText`, then the item's `id`.
 *
 * @param item The MenuItem to render.
 * @param isVisible `true` if the item is currently visible; affects visual styling and the toggle icon.
 * @param onVisibilityToggle Callback invoked when the visibility toggle is pressed.
 */
@Composable
fun MenuItemRow(
    item: MenuItem,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit
) {
    val itemLabel = item.labelResource?.let { stringResource(it) } ?: item.userProvidedText ?: item.id

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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Item label
            Text(
                text = itemLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isVisible) FontWeight.Normal else FontWeight.Light,
                color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            // Visibility toggle
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