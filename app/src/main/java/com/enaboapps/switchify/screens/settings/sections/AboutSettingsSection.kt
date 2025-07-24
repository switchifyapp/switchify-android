package com.enaboapps.switchify.screens.settings.sections

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.components.ScrollableView

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName

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
        FullWidthButton(
            textResId = R.string.button_website,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, websiteUrl.toUri()))
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        FullWidthButton(
            textResId = R.string.button_privacy_policy,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, privacyPolicyUrl.toUri()))
            }
        )
    }
}