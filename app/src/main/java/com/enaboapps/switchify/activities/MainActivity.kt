package com.enaboapps.switchify.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.nav.NavGraph
import com.enaboapps.switchify.service.custom.actions.store.ActionStore
import com.enaboapps.switchify.utils.Logger

class MainActivity : ComponentActivity() {
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var actionStore: ActionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeManagers()

        Logger.logEvent("Launched Switchify")

        setContent {
            val navController = rememberNavController()

            SwitchifyTheme {
                NavGraph(navController = navController)
            }
        }
    }

    private fun initializeManagers() {
        // Initialize PreferenceManager and Logger
        preferenceManager = PreferenceManager(this)
        preferenceManager.enableSync()
        preferenceManager.preferenceSync.apply {
            retrieveSettingsFromFirestore()
            listenForSettingsChangesOnRemote()
        }
        preferenceManager.migrateToProtectedStorage()
        Logger.init(this)

        // Initialize ActionStore
        actionStore = ActionStore(this)
        actionStore.pullActionsFromFirestore()

        // Initialize IAP
        IAPHandler.initialize(this)
    }
}
