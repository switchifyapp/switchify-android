package com.enaboapps.switchify.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.backend.data.FileManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.nav.NavGraph
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import com.enaboapps.switchify.utils.Resources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var fileManager: FileManager

    private val scope = CoroutineScope(Dispatchers.IO)

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
        // Initialize FileManager
        fileManager = FileManager.create(this)

        // Initialize PreferenceManager
        preferenceManager = PreferenceManager(this)
        preferenceManager.enableSync()
        preferenceManager.preferenceSync.apply {
            retrieveSettingsFromSupabase()
        }

        // Initialize Logger
        Logger.init(this)

        // Initialize Resources
        Resources.init(this)

        // Initialize IAP
        IAPHandler.initialize(this)

        Handler(Looper.getMainLooper()).postDelayed({
            SwitchEventStore.getInstance().initialize(this)
        }, 1000)

        // Migrate files from regular storage to device protected storage
        migrateFromRegularStorage()
    }

    /**
     * Migrates files from regular storage to device protected storage.
     */
    private fun migrateFromRegularStorage() {
        scope.launch {
            fileManager.migrateFromRegularStorage(this@MainActivity)
        }
        preferenceManager.migrateToProtectedStorage()
    }
}
