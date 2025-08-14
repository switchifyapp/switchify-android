package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.switches.SwitchEventBus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Initial load
            val initialCameraSwitches = store.getSwitchEvents()
                .filter { it.type == SWITCH_EVENT_TYPE_CAMERA }
            _uiState.value = _uiState.value.copy(
                cameraSwitches = initialCameraSwitches,
                isLoading = false
            )

            // Listen for updates via Flow instead of LocalBroadcastManager
            SwitchEventBus.switchEventsUpdated.collect {
                val cameraSwitches = store.getSwitchEvents()
                    .filter { it.type == SWITCH_EVENT_TYPE_CAMERA }
                _uiState.value = _uiState.value.copy(
                    cameraSwitches = cameraSwitches
                )
            }
        }
    }

}

data class CameraSwitchesUiState(
    val cameraSwitches: List<SwitchEvent> = emptyList(),
    val isLoading: Boolean = false
)