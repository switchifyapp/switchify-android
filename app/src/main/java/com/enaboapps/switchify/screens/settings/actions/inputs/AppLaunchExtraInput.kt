package com.enaboapps.switchify.screens.settings.actions.inputs

import androidx.compose.runtime.Composable
import com.enaboapps.switchify.service.actions.custom.store.data.ActionExtra
import com.enaboapps.switchify.utils.AppLauncher

@Composable
fun AppLaunchExtraInput(
    selectedExtra: ActionExtra?,
    onExtraUpdated: (ActionExtra?) -> Unit,
    onExtraValidated: (Boolean) -> Unit
) {
    AppLaunchPicker(
        initialApp = selectedExtra?.let {
            AppLauncher.AppInfo(
                it.appName,
                it.appPackage
            )
        },
        onAppSelected = { appInfo ->
            onExtraUpdated(
                ActionExtra(
                    appName = appInfo.displayName,
                    appPackage = appInfo.packageName
                )
            )
            onExtraValidated(true)
        }
    )
}
