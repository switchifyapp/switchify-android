package com.enaboapps.switchify.screens

import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.Section

@Composable
fun DebugScreen(navController: NavController) {
    var isProEnabled by remember { mutableStateOf(IAPHandler.hasPurchasedPro()) }

    BaseView(
        title = "Debug Settings",
        navController = navController
    ) {
        Section("Test Pro") {
            PreferenceSwitch(
                title = "Pro Enabled",
                summary = "Enable or disable pro features for testing purposes",
                checked = isProEnabled,
                onCheckedChange = { enabled ->
                    isProEnabled = enabled
                    IAPHandler.setProStatus(enabled)
                }
            )
        }
    }
} 