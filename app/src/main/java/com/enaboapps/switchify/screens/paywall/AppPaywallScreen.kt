package com.enaboapps.switchify.screens.paywall

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.iap.IAPHandler.PurchaseState
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.FullWidthButton
import com.enaboapps.switchify.screens.paywall.model.AppPaywallScreenModel
import com.revenuecat.purchases.ui.revenuecatui.ExperimentalPreviewRevenueCatUIPurchasesAPI
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions

@OptIn(ExperimentalPreviewRevenueCatUIPurchasesAPI::class)
@Composable
fun AppPaywallScreen(navController: NavController) {
    val model = AppPaywallScreenModel()

    val purchaseState = IAPHandler.purchaseState.collectAsState()

    val options = PaywallDialogOptions.Builder()
        .setListener(model)
        .setDismissRequest { navController.popBackStack() }
        .build()

    if (purchaseState.value == PurchaseState.Success) {
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
            FullWidthButton(
                textResId = R.string.button_done,
                onClick = {
                    navController.popBackStack()
                }
            )
        }
    } else {
        PaywallDialog(options)
    }
}