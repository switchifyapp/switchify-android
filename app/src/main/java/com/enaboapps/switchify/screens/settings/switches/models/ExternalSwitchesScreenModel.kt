package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.switches.SwitchEventBus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Initial load
            val initialExternalSwitches = store.getSwitchEvents()
                .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
            _uiState.value = _uiState.value.copy(
                externalSwitches = initialExternalSwitches,
                isLoading = false
            )

            // Listen for updates via Flow instead of LocalBroadcastManager
            SwitchEventBus.switchEventsUpdated.collect {
                val externalSwitches = store.getSwitchEvents()
                    .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
                _uiState.value = _uiState.value.copy(
                    externalSwitches = externalSwitches
                )
            }
        }
    }

}

data class ExternalSwitchesUiState(
    val externalSwitches: List<SwitchEvent> = emptyList(),
    val isLoading: Boolean = false
)