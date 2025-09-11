package com.enaboapps.switchify.screens.settings.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.techniques.AccessTechnique
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccessTechniqueSettingsUiState(
    val currentTechnique: String = AccessTechnique.Technique.ITEM_SCAN,
    val availableTechniques: List<String> = emptyList()
)

class AccessTechniqueSettingsModel(context: Context) : ViewModel() {
    private val preferenceManager = PreferenceManager(context)
    private val scanSettings = ScanSettings(context)

    private val _uiState = MutableStateFlow(
        AccessTechniqueSettingsUiState(
            currentTechnique = preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE),
            availableTechniques = computeAvailable()
        )
    )
    val uiState: StateFlow<AccessTechniqueSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ServiceBridge.serviceEvents.collect { event ->
                when (event) {
                    is ServiceBridge.ServiceEvent.ConfigurationUpdated,
                    is ServiceBridge.ServiceEvent.TechniqueEnforced -> {
                        _uiState.value = _uiState.value.copy(
                            currentTechnique = preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE),
                            availableTechniques = computeAvailable()
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    fun selectTechnique(value: String) {
        preferenceManager.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE,
            value
        )
        _uiState.value = _uiState.value.copy(currentTechnique = value)
        ServiceBridge.sendCommand(
            ServiceBridge.ServiceCommand.UpdateConfiguration(
                PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE,
                value
            )
        )
    }

    private fun computeAvailable(): List<String> {
        return listOf(
            AccessTechnique.Technique.ITEM_SCAN,
            AccessTechnique.Technique.POINT_SCAN,
            AccessTechnique.Technique.RADAR
        )
    }
}

