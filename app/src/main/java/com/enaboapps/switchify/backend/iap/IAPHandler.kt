package com.enaboapps.switchify.backend.iap

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
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
    private var isRevenueCatInitialized = false

    // StateFlow to observe purchase state changes
    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Initial)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState

    // StateFlow to observe purchase capability changes
    private val _purchaseCapability =
        MutableStateFlow<PurchaseCapability>(PurchaseCapability.Unknown)
    val purchaseCapability: StateFlow<PurchaseCapability> = _purchaseCapability

    /**
     * Represents different states of the purchase process
     */
    sealed class PurchaseState {
        object Initial : PurchaseState()
        object Success : PurchaseState()
        object Error : PurchaseState()
    }

    /**
     * Represents the capability to make purchases
     */
    sealed class PurchaseCapability {
        object Available : PurchaseCapability()
        object Unavailable : PurchaseCapability()
        object Restricted : PurchaseCapability()
        object Unknown : PurchaseCapability()
    }

    /**
     * Checks if the handler has been initialized
     *
     * @return true if initialized, false otherwise
     */
    private fun isConnectedToRevenueCat(): Boolean {
        if (!isRevenueCatInitialized) {
            Log.e(
                TAG,
                "IAPHandler must be connected to RevenueCat before use. Call connect() first."
            )
            return false
        }
        return true
    }

    /**
     * Initializes the IAP handler.
     * This method should be called when the app starts.
     *
     * @param context The application context.
     * @param connectToRevenueCat Whether to connect to RevenueCat
     * @param debugLogsEnabled Enable debug logs for RevenueCat
     * @param onInitialized Callback to be called when initialization is complete
     */
    fun initialize(
        context: Context,
        connectToRevenueCat: Boolean = true,
        debugLogsEnabled: Boolean = BuildConfig.DEBUG,
        onInitialized: () -> Unit = {}
    ) {
        if (isRevenueCatInitialized) {
            Log.e(TAG, "IAPHandler is already initialized")
            onInitialized()
            return
        }
        preferenceManager = PreferenceManager(context)
        if (connectToRevenueCat) {
            connect(context, debugLogsEnabled) {
                onInitialized()
            }
        } else {
            onInitialized()
        }

        Log.d(TAG, "Initialized IAP handler")
    }

    /**
     * Initializes RevenueCat if needed. Safe to call multiple times.
     * Use this in paywall/purchase screens before doing any purchase operations.
     *
     * @param context The context to use for initialization
     * @param onInitialized Callback called when RevenueCat is ready
     */
    fun initIfNeeded(context: Context, onInitialized: () -> Unit = {}) {
        if (isRevenueCatInitialized) {
            onInitialized()
            return
        }

        Log.d(TAG, "Initializing RevenueCat on demand")
        connect(context, BuildConfig.DEBUG, onInitialized)
    }

    /**
     * Connects to RevenueCat
     *
     * @param context The application context
     * @param debugLogsEnabled Enable debug logs for RevenueCat
     * @param onInitialized Callback to be called when initialization is complete
     */
    fun connect(
        context: Context,
        debugLogsEnabled: Boolean = BuildConfig.DEBUG,
        onInitialized: () -> Unit = {}
    ) {
        if (isRevenueCatInitialized) {
            Log.e(TAG, "RevenueCat is already initialized")
            onInitialized()
            return
        }
        isRevenueCatInitialized = true
        val config = PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_PUBLIC_KEY)
            .apply {
                diagnosticsEnabled(debugLogsEnabled)
            }
            .build()
        Purchases.configure(config)
        refreshPurchaseStatus()
        checkPurchaseCapability()
        onInitialized()
    }

    /**
     * Refreshes the current purchase status
     *
     * @param completion The completion block to be called when the status is refreshed
     */
    fun refreshPurchaseStatus(completion: ((Boolean) -> Unit)? = null) {
        if (!isConnectedToRevenueCat() || BuildConfig.DEBUG) {
            completion?.invoke(hasPurchasedPro())
            return
        }
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
     * Runs a block of code if the user has purchased the pro version
     * If the user has not purchased the pro version, a pro feature message is shown and the user is redirected to the pro upgrade screen
     * If we are in direct boot mode, the block is run immediately
     *
     * @param context The application context
     * @param block The block of code to run if the user has purchased the pro version
     * @return The result of the block
     */
    fun runIfProPurchased(context: Context, block: () -> Unit): Boolean {
        if (!DeviceLockObserver(context).isUserUnlocked()) {
            block()
            return true
        }

        if (hasPurchasedPro()) {
            block()
            return true
        } else {
            ServiceMessageHUD.instance.showMessage(
                R.string.pro_feature_message,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
            ServiceUtils().openProUpgrade(context)
        }
        return false
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
            Logger.log(LogEvent.ProCheckedViaSubscription)
        } else if (hasPro) {
            Logger.log(LogEvent.ProCheckedViaPurchase)
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
        if (!isConnectedToRevenueCat()) {
            completion("Error: IAPHandler not initialized")
            return
        }
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
        if (!isConnectedToRevenueCat()) {
            return false
        }
        return preferenceManager.getBooleanValue(PreferenceManager.PREFERENCE_KEY_PRO)
    }

    /**
     * Sets the pro status in local storage
     *
     * @param status The status to set
     */
    fun setProStatus(status: Boolean) {
        if (!isConnectedToRevenueCat()) {
            return
        }
        preferenceManager.setBooleanValue(PreferenceManager.PREFERENCE_KEY_PRO, status)
    }

    /**
     * Checks if the user can make purchases
     *
     * @return True if purchases are available, false otherwise
     */
    fun canMakePurchases(): Boolean {
        return when (_purchaseCapability.value) {
            is PurchaseCapability.Available -> true
            else -> false
        }
    }

    /**
     * Gets the current purchase capability
     *
     * @return Current PurchaseCapability state
     */
    fun getCurrentPurchaseCapability(): PurchaseCapability {
        return _purchaseCapability.value
    }

    /**
     * Gets a human-readable reason for purchase unavailability
     *
     * @return String describing why purchases are unavailable, or null if available
     */
    fun getPurchaseCapabilityReason(): String? {
        return when (_purchaseCapability.value) {
            is PurchaseCapability.Available -> null
            is PurchaseCapability.Unavailable -> "In-app purchases are not available on this device"
            is PurchaseCapability.Restricted -> "Purchases are restricted (parental controls or device policy)"
            is PurchaseCapability.Unknown -> "Purchase capability is being determined..."
        }
    }

    /**
     * Checks purchase capability by attempting to fetch offerings
     * This determines if billing is available and working
     */
    fun checkPurchaseCapability() {
        if (!isRevenueCatInitialized) {
            _purchaseCapability.value = PurchaseCapability.Unavailable
            return
        }

        if (BuildConfig.DEBUG) {
            _purchaseCapability.value = PurchaseCapability.Available
            return
        }

        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                if (offerings.all.isEmpty()) {
                    _purchaseCapability.value = PurchaseCapability.Unavailable
                    Log.w(TAG, "No offerings available - purchases unavailable")
                } else {
                    _purchaseCapability.value = PurchaseCapability.Available
                    Log.d(TAG, "Purchase capability: Available")
                }
            }

            override fun onError(error: PurchasesError) {
                val capability = when {
                    error.message.contains("billing unavailable", ignoreCase = true) ||
                            error.message.contains("billing not available", ignoreCase = true) ||
                            error.message.contains("not supported", ignoreCase = true) -> {
                        Log.w(TAG, "Billing unavailable: ${error.message}")
                        PurchaseCapability.Unavailable
                    }

                    error.message.contains("restricted", ignoreCase = true) ||
                            error.message.contains("parental", ignoreCase = true) -> {
                        Log.w(TAG, "Billing restricted: ${error.message}")
                        PurchaseCapability.Restricted
                    }

                    else -> {
                        Log.e(TAG, "Error checking purchase capability: ${error.message}")
                        PurchaseCapability.Unknown
                    }
                }
                _purchaseCapability.value = capability
            }
        })
    }
}