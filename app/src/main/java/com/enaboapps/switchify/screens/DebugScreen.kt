package com.enaboapps.switchify.screens

import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.Section

@Composable
fun DebugScreen(navController: NavController) {
    var isProEnabled by remember { mutableStateOf(IAPHandler.hasPurchasedPro()) }

    BaseView(
        titleResId = R.string.screen_title_debug,
        navController = navController
    ) {
        Section(titleResId = R.string.debug_section_test_pro) {
            PreferenceSwitch(
                titleResId = R.string.debug_title_pro_enabled,
                summaryResId = R.string.debug_summary_pro_enabled,
                checked = isProEnabled,
                onCheckedChange = { enabled ->
                    isProEnabled = enabled
                    IAPHandler.setProStatus(enabled)
                }
            )
        }
    }
} 