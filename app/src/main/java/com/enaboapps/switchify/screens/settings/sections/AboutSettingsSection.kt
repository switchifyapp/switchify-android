package com.enaboapps.switchify.screens.settings.sections

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.SettingsScreenModel

@Composable
fun AboutSection(
    settingsScreenModel: SettingsScreenModel,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    val telemetryEnabled by settingsScreenModel.telemetryEnabled.observeAsState(false)

    val websiteUrl = "https://switchifyapp.com"
    val privacyPolicyUrl = "https://www.switchifyapp.com/privacy"

    ScrollableView {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Version $version",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        val errorMessage = stringResource(R.string.error_no_app_to_open_link)
        ActionButton(
            textResId = R.string.button_website,
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, websiteUrl.toUri()))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        errorMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        ActionButton(
            textResId = R.string.button_privacy_policy,
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, privacyPolicyUrl.toUri()))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        errorMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
        navController?.let {
            Spacer(modifier = Modifier.height(16.dp))
            ActionButton(
                textResId = R.string.action_feedback,
                onClick = {
                    it.navigate(NavigationRoute.UserFeedback.name)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy / telemetry controls. Gates every Logger.log(..) call.
        Section(titleResId = R.string.settings_section_privacy) {
            PreferenceSwitch(
                titleResId = R.string.settings_title_telemetry,
                summaryResId = R.string.settings_summary_telemetry,
                explanationResId = R.string.settings_explanation_telemetry,
                checked = telemetryEnabled,
                onCheckedChange = { settingsScreenModel.setTelemetryEnabled(it) }
            )
        }
    }
}