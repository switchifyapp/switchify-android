package com.enaboapps.switchify.screens.onboarding.steps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.enaboapps.switchify.components.AccessibilityServiceComponent

@Composable
fun AccessibilityExplanationStep(
    isEnabled: Boolean,
    onEnableService: () -> Unit,
    onContinue: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Use the reusable component
        Box(modifier = Modifier.weight(1f)) {
            AccessibilityServiceComponent(
                isEnabled = isEnabled,
                onEnableService = onEnableService,
                onEnabledCallback = onContinue,
                onRefreshStatus = onRefreshStatus
            )
        }
    }
}
