package com.enaboapps.switchify.screens.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.switches.SwitchEventStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupScreenUiState(
    val switchesInvalidReason: String? = null,
    val isAccessibilityServiceEnabled: Boolean = false,
    val isSetupComplete: Boolean = false
)

class SetupScreenModel(
    private val switchEventStore: SwitchEventStore,
    private val serviceUtils: ServiceUtils,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupScreenUiState())
    val uiState: StateFlow<SetupScreenUiState> = _uiState

    fun init(context: Context) {
        refreshSetupState(context)
    }

    private fun refreshSetupState(context: Context) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    switchesInvalidReason = switchEventStore.isConfigInvalid(context),
                    isAccessibilityServiceEnabled = serviceUtils.isAccessibilityServiceEnabled(
                        context
                    )
                )
            }
        }
    }

    fun checkSwitches(context: Context) {
        _uiState.update { currentState ->
            currentState.copy(
                switchesInvalidReason = switchEventStore.isConfigInvalid(context)
            )
        }
    }

    fun setSetupComplete() {
        viewModelScope.launch {
            preferenceManager.setSetupComplete()
            _uiState.update { currentState ->
                currentState.copy(
                    isSetupComplete = true
                )
            }
        }
    }
}