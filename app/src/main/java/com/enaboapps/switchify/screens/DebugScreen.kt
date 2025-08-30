package com.enaboapps.switchify.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.Section

@Composable
fun DebugScreen(navController: NavController) {
    val context = LocalContext.current
    val preferenceManager = remember { PreferenceManager(context) }

    var trialDisabled by remember {
        mutableStateOf(
            preferenceManager.getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_DEBUG_TRIAL_DISABLED,
                false
            )
        )
    }

    BaseView(
        titleResId = R.string.screen_title_debug,
        navController = navController
    ) {
        Section(titleResId = R.string.debug_section_service_trial) {
            Text(
                text = "Service Trial Information",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = when {
                    trialDisabled -> "TRIALS DISABLED: Unlimited access for all users (debug only)."
                    BuildConfig.DEBUG -> "DEBUG BUILD: 30-second trials for testing. Release builds use 1-hour trials."
                    else -> "Each accessibility service session gets a fresh 1-hour trial with all features enabled. Restart the service anytime for a new trial."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            PreferenceSwitch(
                titleResId = R.string.debug_disable_trials_title,
                summaryResId = R.string.debug_disable_trials_summary,
                checked = trialDisabled,
                onCheckedChange = { disabled ->
                    trialDisabled = disabled
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_DEBUG_TRIAL_DISABLED,
                        disabled
                    )
                }
            )
        }
    }
} 