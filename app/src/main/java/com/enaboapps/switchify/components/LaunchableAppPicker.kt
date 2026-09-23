package com.enaboapps.switchify.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.utils.FavouriteAppsManager
import com.enaboapps.switchify.service.utils.FavouriteAppsManager.FavouriteApp

internal object AppPickerFilter {
    fun filter(apps: List<FavouriteApp>, query: String, excludedPackages: Set<String>): List<FavouriteApp> =
        apps.filter { it.packageName !in excludedPackages && it.appName.contains(query.trim(), true) }
}

@Composable
fun LaunchableAppPicker(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    titleResId: Int = R.string.choose_app,
    excludedPackages: Set<String> = emptySet()
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<FavouriteApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(context) {
        apps = FavouriteAppsManager(context).getAllLaunchableApps()
    }
    val matches = remember(apps, query, excludedPackages) {
        apps?.let { AppPickerFilter.filter(it, query, excludedPackages) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(titleResId), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.button_close))
                    }
                }
                SwitchifyTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                if (matches == null) {
                    Column(Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else if (matches.isEmpty()) {
                    Text(stringResource(R.string.no_matching_apps), modifier = Modifier.padding(24.dp))
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(matches, key = { it.packageName }) { app ->
                            AppNameRow(name = app.appName, onClick = { onSelect(app.packageName) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}