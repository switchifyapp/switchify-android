package com.enaboapps.switchify.screens.paywall

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.iap.IAPHandler.PurchaseCapability
import com.enaboapps.switchify.backend.iap.IAPHandler.PurchaseState
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.screens.paywall.model.AppPaywallScreenModel
import com.revenuecat.purchases.ui.revenuecatui.ExperimentalPreviewRevenueCatUIPurchasesAPI
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions

@OptIn(ExperimentalPreviewRevenueCatUIPurchasesAPI::class)
@Composable
fun AppPaywallScreen(navController: NavController) {
    val context = LocalContext.current
    val model: AppPaywallScreenModel = viewModel()

    LaunchedEffect(Unit) {
        IAPHandler.initIfNeeded(context)
    }

    val purchaseState = IAPHandler.purchaseState.collectAsState()
    val purchaseCapability = IAPHandler.purchaseCapability.collectAsState()

    val options = PaywallDialogOptions.Builder()
        .setRequiredEntitlementIdentifier(IAPHandler.ENTITLEMENT)
        .setListener(model)
        .setDismissRequest { navController.popBackStack() }
        .build()

    when {
        purchaseState.value == PurchaseState.Success -> {
            BaseView(
                titleResId = R.string.screen_title_pro_yours,
                navController = navController,
                enableScroll = false,
                padding = 25.dp
            ) {
                Text(
                    text = stringResource(R.string.pro_thank_you),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActionButton(
                    textResId = R.string.button_done,
                    onClick = {
                        navController.popBackStack()
                    }
                )
            }
        }

        purchaseCapability.value is PurchaseCapability.Unavailable ||
                purchaseCapability.value is PurchaseCapability.Restricted -> {
            BaseView(
                titleResId = R.string.screen_title_upgrade_pro,
                navController = navController,
                enableScroll = false,
                padding = 25.dp
            ) {
                Text(
                    text = IAPHandler.getPurchaseCapabilityReason() ?: "Purchases unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Please check your device settings or contact support if you believe this is an error.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActionButton(
                    textResId = R.string.button_done,
                    onClick = {
                        navController.popBackStack()
                    }
                )
            }
        }

        purchaseCapability.value is PurchaseCapability.Unknown -> {
            BaseView(
                titleResId = R.string.screen_title_upgrade_pro,
                navController = navController,
                enableScroll = false,
                padding = 25.dp
            ) {
                Text(
                    text = "Checking purchase availability...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActionButton(
                    textResId = R.string.button_retry,
                    onClick = {
                        IAPHandler.initIfNeeded(context) {
                            IAPHandler.checkPurchaseCapability()
                        }
                    }
                )
            }
        }

        else -> {
            // In debug builds, show a mock paywall since RevenueCat may not be configured
            if (BuildConfig.DEBUG) {
                DebugPaywallScreen(navController)
            } else {
                PaywallDialog(options)
            }
        }
    }
}

/**
 * Mock paywall screen for debug builds.
 * Shows placeholder UI since RevenueCat requires proper configuration to display the real paywall.
 */
@Composable
private fun DebugPaywallScreen(navController: NavController) {
    BaseView(
        titleResId = R.string.screen_title_upgrade_pro,
        navController = navController,
        enableScroll = false,
        padding = 25.dp
    ) {
        Text(
            text = "Debug Mode - Paywall Preview",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This is a mock paywall for debug builds. The real RevenueCat paywall will be shown in release builds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Switchify Pro includes:\n• All premium features\n• Priority support\n• No ads",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        ActionButton(
            textResId = R.string.button_done,
            onClick = {
                navController.popBackStack()
            }
        )
    }
}
