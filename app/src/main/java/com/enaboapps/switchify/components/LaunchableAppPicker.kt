package com.enaboapps.switchify.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.utils.FavouriteAppsManager

@Composable
fun LaunchableAppPicker(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<FavouriteAppsManager.FavouriteApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(context) {
        apps = FavouriteAppsManager(context).getAllLaunchableApps()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_app)) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_apps)) }, modifier = Modifier.fillMaxWidth())
                val matches = apps?.filter {
                    it.appName.contains(query, true) || it.packageName.contains(query, true)
                }
                if (matches == null) CircularProgressIndicator()
                else if (matches.isEmpty()) Text(stringResource(R.string.no_matching_apps))
                else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(matches, key = { it.packageName }) { app ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(app.packageName) }.padding(12.dp)) {
                            Text(app.appName)
                            Text(app.packageName)
                        }
                    }
                }
            }
        }
    )
}
