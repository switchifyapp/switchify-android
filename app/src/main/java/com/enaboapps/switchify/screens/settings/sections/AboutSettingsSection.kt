package com.enaboapps.switchify.screens.settings.sections

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.InfoCard
import com.enaboapps.switchify.components.PanelListRow
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.AboutSettingsModel
import com.enaboapps.switchify.theme.Dimens
import com.enaboapps.switchify.tips.TipNotifications

@Composable
fun AboutSection(
    settingsScreenModel: AboutSettingsModel,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    val telemetryEnabled by settingsScreenModel.telemetryEnabled.observeAsState(false)

    val websiteUrl = "https://www.theswitchify.app"
    val userGuideUrl = "https://android.theswitchify.app"
    val privacyPolicyUrl = "https://www.theswitchify.app/privacy"
    val errorMessage = stringResource(R.string.error_no_app_to_open_link)

    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    val tipNotificationsEnabled by settingsScreenModel.tipNotificationsEnabled.observeAsState(false)
    var tipNotificationsDeniedCount by remember { mutableIntStateOf(0) }
    val notificationsBlockedMessage = stringResource(R.string.tips_notifications_blocked)
    val onNotificationsBlocked = {
        tipNotificationsDeniedCount++
        Toast.makeText(context, notificationsBlockedMessage, Toast.LENGTH_LONG).show()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) settingsScreenModel.setTipNotificationsEnabled(true) else onNotificationsBlocked()
    }
    val onTipNotificationsChange: (Boolean) -> Unit = { enabled ->
        when {
            !enabled -> settingsScreenModel.setTipNotificationsEnabled(false)
            TipNotifications.canPostNotifications(context) ->
                settingsScreenModel.setTipNotificationsEnabled(true)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            else -> onNotificationsBlocked()
        }
    }

    ScrollableView {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Spacer(modifier = Modifier.height(Dimens.spaceS))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Dimens.spaceXs))
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "v$version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spaceS, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spaceL))

        InfoCard(
            titleResId = R.string.about_card_title,
            descriptionResId = R.string.app_description
        )

        Spacer(modifier = Modifier.height(Dimens.spaceL))

        Section(titleResId = R.string.settings_section_about_links) {
            PanelListRow(
                titleResId = R.string.button_website,
                leadingIcon = Icons.Rounded.Language,
                onClick = { openUrl(websiteUrl) },
                trailing = { ExternalLinkIcon() }
            )
            PanelListRow(
                titleResId = R.string.button_user_guide,
                leadingIcon = Icons.Rounded.MenuBook,
                onClick = { openUrl(userGuideUrl) },
                trailing = { ExternalLinkIcon() }
            )
            PanelListRow(
                titleResId = R.string.button_privacy_policy,
                leadingIcon = Icons.Rounded.PrivacyTip,
                onClick = { openUrl(privacyPolicyUrl) },
                trailing = { ExternalLinkIcon() }
            )
            navController?.let {
                PanelListRow(
                    titleResId = R.string.action_feedback,
                    leadingIcon = Icons.Rounded.Feedback,
                    onClick = { it.navigate(NavigationRoute.UserFeedback.name) }
                )
            }
        }

        if (TipNotifications.isAvailable(context)) {
            Section(titleResId = R.string.settings_section_notifications) {
                key(tipNotificationsDeniedCount) {
                    PreferenceSwitch(
                        titleResId = R.string.settings_title_tip_notifications,
                        summaryResId = R.string.settings_summary_tip_notifications,
                        explanationResId = R.string.settings_explanation_tip_notifications,
                        checked = tipNotificationsEnabled,
                        onCheckedChange = onTipNotificationsChange
                    )
                }
            }
        }

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

@Composable
private fun ExternalLinkIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp)
    )
}
