package com.enaboapps.switchify.screens.paywall.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener

class AppPaywallScreenModel(application: Application) : AndroidViewModel(application),
    PaywallListener {
    override fun onRestoreCompleted(customerInfo: CustomerInfo) {
        super.onRestoreCompleted(customerInfo)

        // Handle restore completion
        IAPHandler.initIfNeeded(getApplication<Application>()) {
            IAPHandler.refreshPurchaseStatus()
        }
    }

    override fun onPurchaseCompleted(
        customerInfo: CustomerInfo,
        storeTransaction: StoreTransaction
    ) {
        super.onPurchaseCompleted(customerInfo, storeTransaction)

        // Handle purchase completion
        IAPHandler.initIfNeeded(getApplication<Application>()) {
            IAPHandler.refreshPurchaseStatus()
        }
    }
}