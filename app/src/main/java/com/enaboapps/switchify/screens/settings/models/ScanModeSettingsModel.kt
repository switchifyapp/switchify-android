package com.enaboapps.switchify.screens.settings.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.scanning.ScanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScanModeSettingsUiState(
    val currentMode: ScanMode = ScanMode.fromId(ScanMode.Modes.MODE_AUTO)
)

class ScanModeSettingsModel(context: Context) : ViewModel() {
    private val preferenceManager = PreferenceManager(context)

    private val _uiState = MutableStateFlow(
        ScanModeSettingsUiState(
            currentMode = ScanMode.fromId(
                preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE)
            )
        )
    )
    val uiState: StateFlow<ScanModeSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ServiceBridge.serviceEvents.collect { event ->
                when (event) {
                    is ServiceBridge.ServiceEvent.ConfigurationUpdated -> {
                        _uiState.value = _uiState.value.copy(
                            currentMode = ScanMode.fromId(
                                preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE)
                            )
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    fun selectMode(mode: ScanMode) {
        preferenceManager.setStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE, mode.id)
        _uiState.value = _uiState.value.copy(currentMode = mode)
        ServiceBridge.sendCommand(
            ServiceBridge.ServiceCommand.UpdateConfiguration(
                PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE,
                mode.id
            )
        )
    }
}

