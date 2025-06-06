package com.enaboapps.switchify.screens.onboarding.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.switches.SwitchConfigInvalidBanner

@Composable
fun SwitchSetupStep(
    navController: NavController,
    switchesValid: Boolean,
    onSwitchesConfigured: () -> Unit,
    onContinue: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    LaunchedEffect(navController) {
        // Check switch status when returning from switch configuration
        navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("switches_configured")
            ?.observeForever { configured ->
                if (configured == true) {
                    onSwitchesConfigured()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.onboarding_switches_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = stringResource(R.string.onboarding_switches_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Switch options
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_switch_options),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SwitchOption(
                    icon = Icons.Default.Keyboard,
                    title = stringResource(R.string.onboarding_switch_keyboard),
                    description = stringResource(R.string.onboarding_switch_keyboard_desc)
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                SwitchOption(
                    icon = Icons.Default.Bluetooth,
                    title = stringResource(R.string.onboarding_switch_bluetooth),
                    description = stringResource(R.string.onboarding_switch_bluetooth_desc)
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                SwitchOption(
                    icon = Icons.Default.CameraAlt,
                    title = stringResource(R.string.onboarding_switch_camera),
                    description = stringResource(R.string.onboarding_switch_camera_desc)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status and action
        AnimatedVisibility(
            visible = !switchesValid,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SwitchConfigInvalidBanner(
                    bannerText = stringResource(R.string.onboarding_no_switches_configured)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FullWidthButton(
                    textResId = R.string.onboarding_configure_switches,
                    onClick = {
                        navController.navigate(NavigationRoute.Switches.name)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = switchesValid,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.onboarding_switches_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(16.dp))

        // Continue button
        FullWidthButton(
            textResId = R.string.onboarding_continue,
            onClick = onContinue,
            enabled = switchesValid
        )
    }
}

@Composable
private fun SwitchOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}