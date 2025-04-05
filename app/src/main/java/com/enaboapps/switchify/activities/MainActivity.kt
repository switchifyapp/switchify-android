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
import com.enaboapps.switchify.service.actions.custom.store.ActionStore
import com.enaboapps.switchify.service.gestures.data.store.GesturePatternStore
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.utils.Logger
import com.enaboapps.switchify.utils.Resources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var actionStore: ActionStore
    private lateinit var gesturePatternStore: GesturePatternStore
    private lateinit var fileManager: FileManager

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setup()

        Logger.logEvent("Launched Switchify")

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
            retrieveSettingsFromFirestore()
            listenForSettingsChangesOnRemote()
        }

        // Initialize GesturePatternStore
        gesturePatternStore = GesturePatternStore(this)
        gesturePatternStore.pullPatternsFromFirestore()

        // Initialize Logger
        Logger.init(this)

        // Initialize Resources
        Resources.init(this)

        // Initialize ActionStore
        actionStore = ActionStore(this)
        actionStore.pullActionsFromFirestore()

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
