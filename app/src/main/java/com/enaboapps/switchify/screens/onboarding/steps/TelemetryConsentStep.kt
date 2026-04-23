package com.enaboapps.switchify.screens.onboarding.steps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.theme.Dimens

/**
 * Onboarding step that asks the user whether they want to share diagnostic data
 * (analytics events + crash reports) with the Switchify team. Default is off
 * (opt-in); the user's choice is written immediately via
 * [com.enaboapps.switchify.screens.onboarding.OnboardingViewModel.setTelemetryConsent].
 */
@Composable
fun TelemetryConsentStep(
    onChoice: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val privacyPolicyUrl = "https://www.switchifyapp.com/privacy"
    val errorMessage = stringResource(R.string.error_no_app_to_open_link)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(Dimens.spaceL + Dimens.spaceM))

        Text(
            text = stringResource(R.string.onboarding_telemetry_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = Dimens.spaceS)
        )

        Text(
            text = stringResource(R.string.onboarding_telemetry_subtitle),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spaceL)
        )

        Text(
            text = stringResource(R.string.onboarding_telemetry_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = Dimens.spaceM)
        )

        Text(
            text = stringResource(R.string.onboarding_telemetry_privacy_note),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spaceL))

        ActionButton(
            textResId = R.string.button_privacy_policy,
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, privacyPolicyUrl.toUri()))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        ActionButton(
            textResId = R.string.onboarding_telemetry_accept,
            onClick = { onChoice(true) }
        )

        Spacer(modifier = Modifier.height(Dimens.spaceS))

        ActionButton(
            textResId = R.string.onboarding_telemetry_decline,
            onClick = { onChoice(false) }
        )
    }
}
