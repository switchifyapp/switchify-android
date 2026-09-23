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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ExternalSwitchesScreenModel : ViewModel() {
    private val store = SwitchEventStore.getInstance()
    private val _uiState = MutableStateFlow(ExternalSwitchesUiState())
    val uiState: StateFlow<ExternalSwitchesUiState> = _uiState

    private val numberOfSwitchesLimit = 3

    fun setup(context: Context, profileId: String? = null) {
        observeExternalSwitches(context, profileId)
    }


    fun isAnotherSwitchAllowed(): Boolean {
        return true
    }

    private fun observeExternalSwitches(context: Context, profileId: String?) {
        viewModelScope.launch {
            store.initializeAsync(context)
            val initialExternalSwitches = store.getSwitchEvents(profileId)
                .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
            _uiState.value = _uiState.value.copy(
                externalSwitches = initialExternalSwitches,
                isLoading = false
            )
        }

        // Listen for updates via ServiceBridge
        ServiceBridge.serviceEvents
            .filter {
                it is ServiceBridge.ServiceEvent.SwitchEventsUpdated ||
                    it is ServiceBridge.ServiceEvent.SwitchProfilesUpdated
            }
            .onEach {
                val externalSwitches = store.getSwitchEvents(profileId)
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
