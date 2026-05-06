package com.enaboapps.switchify.screens.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.theme.Dimens

@Composable
fun HeadControlExplanationStep(
    navController: NavController,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val serviceUtils = remember { ServiceUtils() }
    val isServiceActive = remember { serviceUtils.isAccessibilityServiceEnabled(context) }
    val hasCameraPermission =
        remember { CameraPermissionManager.getInstance(context).hasPermission() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Dimens.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Head Control Icon
        Icon(
            painter = painterResource(id = R.drawable.ic_head_control_pointer),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = Dimens.spaceM)
        )

        Text(
            text = stringResource(R.string.onboarding_head_control_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = Dimens.spaceXs)
        )

        Text(
            text = stringResource(R.string.onboarding_head_control_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spaceL + Dimens.spaceS)
        )

        // Features Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(Dimens.spaceM),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
            ) {
                Text(
                    text = "Key Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = Dimens.spaceXs)
                )

                HeadControlFeatureItem(
                    text = stringResource(R.string.onboarding_head_control_feature_movement)
                )
                HeadControlFeatureItem(
                    text = stringResource(R.string.onboarding_head_control_feature_gestures)
                )
                HeadControlFeatureItem(
                    text = stringResource(R.string.onboarding_head_control_feature_complement)
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceL))

        // Requirements Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(Dimens.spaceM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.spaceS))
                Text(
                    text = stringResource(R.string.onboarding_head_control_requirements),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceL))

        // Status indicators
        if (isServiceActive && hasCameraPermission) {
            Text(
                text = "✅ Ready to use! Find the Head Control toggle on your Home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isServiceActive) {
                    Text(
                        text = "📋 Complete accessibility service setup first",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                if (!hasCameraPermission) {
                    Text(
                        text = "📷 Camera permission will be needed",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceL))

        // Learn More Link
        NavRouteLink(
            titleResId = R.string.onboarding_head_control_learn_more,
            summaryResId = R.string.head_control_settings_summary,
            navController = navController,
            route = NavigationRoute.HeadControlSettings.name
        )

        Spacer(modifier = Modifier.height(Dimens.spaceL + Dimens.spaceS))

        // Continue button
        ActionButton(
            textResId = R.string.onboarding_continue,
            onClick = onContinue
        )
    }
}

@Composable
private fun HeadControlFeatureItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(Dimens.spaceS))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}