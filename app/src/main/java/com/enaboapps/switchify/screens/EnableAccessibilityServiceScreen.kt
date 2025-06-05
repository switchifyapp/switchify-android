package com.enaboapps.switchify.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.AccessibilityServiceComponent
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.utils.Logger

@Composable
fun EnableAccessibilityServiceScreen(navController: NavController) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(ServiceUtils().isAccessibilityServiceEnabled(context)) }

    BaseView(
        titleResId = R.string.screen_title_accessibility_service,
        navController = navController,
        enableScroll = false
    ) {
        AccessibilityServiceComponent(
            isEnabled = isEnabled,
            onEnableService = {
                ServiceUtils().openAccessibilitySettings(context)
                Logger.logEvent("Opened Accessibility Settings")
            },
            onEnabledCallback = {
                navController.popBackStack()
            },
            onRefreshStatus = {
                isEnabled = ServiceUtils().isAccessibilityServiceEnabled(context)
            }
        )
    }
}