package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CameraSwitchesScreenModel : ViewModel() {
    private val store = SwitchEventStore.getInstance()
    private val _uiState = MutableStateFlow(CameraSwitchesUiState())
    val uiState: StateFlow<CameraSwitchesUiState> = _uiState

    private val numberOfSwitchesLimit = 3

    fun setup(context: Context) {
        observeCameraSwitches(context)
    }


    fun isAnotherSwitchAllowed(): Boolean {
        return true
    }

    private fun observeCameraSwitches(context: Context) {
        // Initial load
        val initialCameraSwitches = store.getSwitchEvents()
            .filter { it.type == SWITCH_EVENT_TYPE_CAMERA }
        _uiState.value = _uiState.value.copy(
            cameraSwitches = initialCameraSwitches,
            isLoading = false
        )

        // Listen for updates via ServiceBridge
        ServiceBridge.serviceEvents
            .filterIsInstance<ServiceBridge.ServiceEvent.SwitchEventsUpdated>()
            .onEach {
                val cameraSwitches = store.getSwitchEvents()
                    .filter { it.type == SWITCH_EVENT_TYPE_CAMERA }
                _uiState.value = _uiState.value.copy(
                    cameraSwitches = cameraSwitches
                )
            }
            .launchIn(viewModelScope)
    }

}

data class CameraSwitchesUiState(
    val cameraSwitches: List<SwitchEvent> = emptyList(),
    val isLoading: Boolean = false
)