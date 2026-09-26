package com.enaboapps.switchify.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.backend.data.FileManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.nav.NavGraph
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var fileManager: FileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setup()

        Logger.log(LogEvent.AppLaunched)

        setContent {
            val navController = rememberNavController()

            SwitchifyTheme {
                NavGraph(navController = navController)
            }
        }
    }

    private fun setup() {
        // Initialize Supabase client
        initializeSupabase()

        // Initialize FileManager
        fileManager = FileManager.create(this)

        // Initialize PreferenceManager
        preferenceManager = PreferenceManager(this)
        preferenceManager.enableSync()

        // Initialize IAP
        IAPHandler.initialize(this)

        // Listen for CustomerInfo updates to keep entitlement state fresh
        try {
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener {
                IAPHandler.refreshPurchaseStatus()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to attach Purchases listener", e)
            Logger.log(
                LogEvent.AppSetupStageFailed,
                data = mapOf(
                    "result" to "failure",
                    "stage" to "purchases_listener_attach",
                    "reason" to "exception"
                ),
                throwable = e
            )
        }

        val appContext = applicationContext
        lifecycleScope.launch {
            delay(1000)
            SwitchEventStore.getInstance().initialize(appContext)
        }


        // Migrate files from regular storage to device protected storage
        migrateFromRegularStorage()
    }

    private fun initializeSupabase() {
        try {
            // Force initialization of Supabase client
            com.enaboapps.switchify.backend.supabase.SupabaseClient.initialize()
            android.util.Log.i("MainActivity", "Supabase client initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize Supabase client", e)
            Logger.log(
                LogEvent.AppSetupStageFailed,
                data = mapOf(
                    "result" to "failure",
                    "stage" to "supabase_initialize",
                    "reason" to "exception"
                ),
                throwable = e
            )
        }
    }

    /**
     * Migrates files from regular storage to device protected storage.
     */
    private fun migrateFromRegularStorage() {
        val appContext = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                fileManager.migrateFromRegularStorage(appContext)
            } catch (e: Exception) {
                Logger.log(
                    LogEvent.AppSetupStageFailed,
                    data = mapOf(
                        "result" to "failure",
                        "stage" to "file_migration",
                        "reason" to "exception"
                    ),
                    throwable = e
                )
            }
        }
        try {
            preferenceManager.migrateToProtectedStorage()
        } catch (e: Exception) {
            Logger.log(
                LogEvent.AppSetupStageFailed,
                data = mapOf(
                    "result" to "failure",
                    "stage" to "preferences_migration",
                    "reason" to "exception"
                ),
                throwable = e
            )
        }
    }
}
