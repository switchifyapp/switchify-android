package com.enaboapps.switchify.screens.onboarding.steps

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.AccessibilityServiceComponent
import com.enaboapps.switchify.components.FullWidthButton

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
