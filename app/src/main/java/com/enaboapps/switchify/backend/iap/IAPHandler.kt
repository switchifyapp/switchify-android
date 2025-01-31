package com.enaboapps.switchify.backend.iap

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.utils.Logger
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * This object handles in-app purchases using RevenueCat.
 * It provides functionality for purchasing, restoring purchases,
 * and checking subscription status.
 */
object IAPHandler {
    private const val TAG = "IAPHandler"
    const val ENTITLEMENT = "pro"
    private lateinit var preferenceManager: PreferenceManager

    // StateFlow to observe purchase state changes
    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Initial)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState

    /**
     * Represents different states of the purchase process
     */
    sealed class PurchaseState {
        object Initial : PurchaseState()
        object Success : PurchaseState()
        object Error : PurchaseState()
    }

    /**
     * Initializes the IAP handler.
     * This method should be called when the app starts.
     *
     * @param context The application context.
     * @param debugLogsEnabled Enable debug logs for RevenueCat
     */
    fun initialize(context: Context, debugLogsEnabled: Boolean = BuildConfig.DEBUG) {
        val config = PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_PUBLIC_KEY)
            .apply {
                if (debugLogsEnabled) {
                    diagnosticsEnabled(true)
                }
            }
            .build()

        Purchases.configure(config)
        preferenceManager = PreferenceManager(context)

        // Fetch initial customer info
        refreshPurchaseStatus()

        Log.d(TAG, "Initialized IAP handler")
    }

    /**
     * Refreshes the current purchase status
     *
     * @param completion The completion block to be called when the status is refreshed
     */
    fun refreshPurchaseStatus(completion: ((Boolean) -> Unit)? = null) {
        Purchases.sharedInstance.getCustomerInfo(
            object : ReceiveCustomerInfoCallback {
                override fun onError(error: PurchasesError) {
                    Log.e(TAG, "Error refreshing status: ${error.message}")
                    _purchaseState.value = PurchaseState.Error
                    completion?.invoke(false)
                }

                override fun onReceived(customerInfo: CustomerInfo) {
                    checkProPurchase(customerInfo)
                    completion?.invoke(hasPurchasedPro())
                }
            }
        )
    }

    /**
     * Checks if the user has purchased the pro version
     *
     * @param customerInfo Customer info from RevenueCat
     */
    private fun checkProPurchase(customerInfo: CustomerInfo) {
        val hasPro = customerInfo.entitlements[ENTITLEMENT]?.isActive == true
        val isSubscribed = customerInfo.activeSubscriptions.isNotEmpty()
        if (isSubscribed) {
            Logger.logEvent("Pro checked via subscription")
        } else if (hasPro) {
            Logger.logEvent("Pro checked via purchase")
        }
        setProStatus(hasPro)
        _purchaseState.value = if (hasPro) PurchaseState.Success else PurchaseState.Initial
    }

    /**
     * Gets the information in string format about the current status of the pro purchase
     *
     * @return The information in string format about the current status of the pro purchase
     */
    fun getProStatus(completion: (String) -> Unit) {
        Purchases.sharedInstance.getCustomerInfo(
            object : ReceiveCustomerInfoCallback {
                override fun onError(error: PurchasesError) {
                    completion("Error getting pro status: ${error.message}")
                }

                override fun onReceived(customerInfo: CustomerInfo) {
                    val hasPro = customerInfo.entitlements[ENTITLEMENT]?.isActive == true
                    val isSubscribed = customerInfo.activeSubscriptions.isNotEmpty()
                    if (isSubscribed) {
                        completion("You have an active subscription to Switchify")
                    } else if (hasPro) {
                        completion("You have purchased Switchify Pro")
                    } else {
                        completion("You have not purchased Switchify Pro")
                    }
                }
            })
    }

    /**
     * Checks if the user has purchased the pro version
     *
     * @return True if the user has purchased pro, false otherwise
     */
    fun hasPurchasedPro(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.PREFERENCE_KEY_PRO)
    }

    /**
     * Sets the pro status in local storage
     *
     * @param status The status to set
     */
    private fun setProStatus(status: Boolean) {
        preferenceManager.setBooleanValue(PreferenceManager.PREFERENCE_KEY_PRO, status)
    }
}