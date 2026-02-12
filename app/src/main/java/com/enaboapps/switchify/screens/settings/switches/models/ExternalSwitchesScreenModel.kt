package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ExternalSwitchesScreenModel : ViewModel() {
    private val store = SwitchEventStore.getInstance()
    private val _uiState = MutableStateFlow(ExternalSwitchesUiState())
    val uiState: StateFlow<ExternalSwitchesUiState> = _uiState

    private val numberOfSwitchesLimit = 3

    fun setup(context: Context) {
        observeExternalSwitches(context)
    }


    fun isAnotherSwitchAllowed(): Boolean {
        return true
    }

    private fun observeExternalSwitches(context: Context) {
        // Initial load
        val initialExternalSwitches = store.getSwitchEvents()
            .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
        _uiState.value = _uiState.value.copy(
            externalSwitches = initialExternalSwitches,
            isLoading = false
        )

        // Listen for updates via ServiceBridge
        ServiceBridge.serviceEvents
            .filterIsInstance<ServiceBridge.ServiceEvent.SwitchEventsUpdated>()
            .onEach {
                val externalSwitches = store.getSwitchEvents()
                    .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
                _uiState.value = _uiState.value.copy(
                    externalSwitches = externalSwitches
                )
            }
            .launchIn(viewModelScope)
    }

}

data class ExternalSwitchesUiState(
    val externalSwitches: List<SwitchEvent> = emptyList(),
    val isLoading: Boolean = false
)