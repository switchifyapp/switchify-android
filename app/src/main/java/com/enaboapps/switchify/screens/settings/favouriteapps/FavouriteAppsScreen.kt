package com.enaboapps.switchify.screens.settings.favouriteapps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.ReorderableList
import com.enaboapps.switchify.components.SwitchifyTextField
import com.enaboapps.switchify.components.animatedPressContainerColor
import com.enaboapps.switchify.service.utils.FavouriteAppsManager.FavouriteApp

@Composable
fun FavouriteAppsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: FavouriteAppsScreenModel = viewModel { FavouriteAppsScreenModel(context) }
    val favouriteApps by viewModel.favouriteApps.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    BaseView(
        titleResId = R.string.favourite_apps_title,
        navController = navController,
        enableScroll = false,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.loadAllApps()
                    showPicker = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.favourite_apps_add)
                )
            }
        }
    ) {
        if (favouriteApps.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.favourite_apps_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            ReorderableList(
                items = favouriteApps,
                onMove = { from, to -> viewModel.reorderApps(from, to) },
                key = { it.packageName }
            ) { app, _, reorderControls ->
                FavouriteAppItem(
                    app = app,
                    onRemove = { viewModel.removeApp(app) },
                    reorderControls = reorderControls
                )
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            viewModel = viewModel,
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun FavouriteAppItem(
    app: FavouriteApp,
    onRemove: () -> Unit,
    reorderControls: @Composable () -> Unit
) {
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 360.dp
            if (compact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        reorderControls()
                        RemoveFavouriteAppButton(onRemove)
                    }
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reorderControls()
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    RemoveFavouriteAppButton(onRemove)
                }
            }
        }
    }
}

@Composable
private fun RemoveFavouriteAppButton(onRemove: () -> Unit) {
    IconButton(onClick = onRemove) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Remove",
            tint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun AppPickerDialog(
    viewModel: FavouriteAppsScreenModel,
    onDismiss: () -> Unit
) {
    val allApps by viewModel.allApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val favouriteApps by viewModel.favouriteApps.collectAsState()
    val favouritePackageNames = remember(favouriteApps) {
        favouriteApps.map { it.packageName }.toSet()
    }

    val filteredApps = remember(allApps, searchQuery, favouritePackageNames) {
        allApps
            .filter { it.packageName !in favouritePackageNames }
            .filter {
                searchQuery.isBlank() ||
                        it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
    }

    Dialog(
        onDismissRequest = {
            viewModel.updateSearchQuery("")
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.favourite_apps_picker_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = {
                        viewModel.updateSearchQuery("")
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                // Search field
                SwitchifyTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.favourite_apps_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppPickerItem(
                                app = app,
                                onClick = {
                                    viewModel.addApp(app)
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerItem(
    app: FavouriteApp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rowColor = animatedPressContainerColor(
        interactionSource = interactionSource,
        idleColor = Color.Transparent,
        pressedColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.favourite_apps_add),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
