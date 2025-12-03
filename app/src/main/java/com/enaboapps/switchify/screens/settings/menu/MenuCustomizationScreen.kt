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
