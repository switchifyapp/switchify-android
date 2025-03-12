package com.enaboapps.switchify.screens.settings.actions.inputs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.utils.AppLauncher
import com.enaboapps.switchify.utils.Resources

@Composable
fun AppLaunchPicker(
    initialApp: AppLauncher.AppInfo? = null,
    onAppSelected: (AppLauncher.AppInfo) -> Unit
) {
    val context = LocalContext.current
    val appLauncher = remember { AppLauncher(context) }
    val allApps = remember { appLauncher.getInstalledApps() }

    Picker(
        titleResId = R.string.action_select_app,
        selectedItem = initialApp,
        items = allApps,
        onItemSelected = { app ->
            onAppSelected(app)
        },
        itemToString = { it.displayName },
        itemDescription = {
            Resources.getString(
                R.string.action_select_app_description,
                it.displayName
            )
        }
    )
}
