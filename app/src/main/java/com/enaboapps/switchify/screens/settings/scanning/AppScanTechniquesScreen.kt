package com.enaboapps.switchify.screens.settings.scanning

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.LaunchableAppPicker
import com.enaboapps.switchify.service.scanning.AppScanTechniqueSettings
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.utils.AppLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AppScanTechniquesScreenModel(context: Context) : ViewModel() {
    private val settings = AppScanTechniqueSettings(context.applicationContext)
    private val preferences = PreferenceManager(context.applicationContext)
    private val currentRules = MutableStateFlow(settings.rules())
    val rules = currentRules.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PreferenceManager.PREFERENCE_KEY_APP_SCAN_TECHNIQUES) currentRules.value = settings.rules()
    }
    init { preferences.registerChangeListener(listener) }
    fun setRule(app: String, technique: String?) = settings.setRule(app, technique)
    override fun onCleared() { preferences.unregisterChangeListener(listener) }
}

@Composable
fun AppScanTechniquesScreen(navController: NavController) {
    val context = LocalContext.current
    val model = viewModel { AppScanTechniquesScreenModel(context) }
    val rules by model.rules.collectAsState()
    var choosingApp by rememberSaveable { mutableStateOf(false) }
    var editingApp by rememberSaveable { mutableStateOf<String?>(null) }
    BaseView(titleResId = R.string.app_scan_techniques_title, navController = navController, enableScroll = false) {
        TextButton(onClick = { choosingApp = true }) { Text(stringResource(R.string.app_scan_techniques_add)) }
        if (rules.isEmpty()) Text(stringResource(R.string.app_scan_techniques_empty), Modifier.padding(16.dp))
        LazyColumn {
            items(rules.keys.sorted(), key = { it }) { app ->
                Column(Modifier.fillMaxWidth().clickable { editingApp = app }.padding(16.dp)) {
                    Text(AppLauncher(context).label(app))
                    Text(AccessTechnique.getName(rules.getValue(app)))
                }
            }
        }
    }
    if (choosingApp) LaunchableAppPicker(onDismiss = { choosingApp = false }, onSelect = {
        choosingApp = false
        editingApp = it
    })
    editingApp?.let { app ->
        AlertDialog(onDismissRequest = { editingApp = null },
            title = { Text(AppLauncher(context).label(app)) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editingApp = null }) { Text(stringResource(android.R.string.cancel)) } },
            text = {
                Column {
                    AppScanTechniqueSettings.techniques.forEach { technique ->
                        TextButton(onClick = { model.setRule(app, technique); editingApp = null }) {
                            Text(AccessTechnique.getName(technique))
                        }
                    }
                    TextButton(onClick = { model.setRule(app, null); editingApp = null }) {
                        Text(stringResource(R.string.app_scan_techniques_default))
                    }
                }
            })
    }
}
