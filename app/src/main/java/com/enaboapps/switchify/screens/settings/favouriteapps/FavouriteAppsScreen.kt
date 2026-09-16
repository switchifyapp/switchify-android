package com.enaboapps.switchify.screens.settings.favouriteapps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.AppNameRow
import com.enaboapps.switchify.components.LaunchableAppPicker
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.ReorderableList
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
        LaunchableAppPicker(
            onDismiss = { showPicker = false },
            onSelect = { viewModel.addApp(it) },
            titleResId = R.string.favourite_apps_picker_title,
            excludedPackages = favouriteApps.map { it.packageName }.toSet()
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
        AppNameRow(
            name = app.appName,
            leadingContent = reorderControls,
            trailingContent = { RemoveFavouriteAppButton(onRemove) }
        )
    }
}

@Composable
private fun RemoveFavouriteAppButton(onRemove: () -> Unit) {
    IconButton(onClick = onRemove) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(R.string.button_delete),
            tint = MaterialTheme.colorScheme.error
        )
    }
}
