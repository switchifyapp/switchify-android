package com.enaboapps.switchify.screens.settings.models

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import kotlinx.coroutines.launch

class AboutSettingsModel(context: Context) : ViewModel() {
    private val preferenceManager = PreferenceManager(context)

    private val _telemetryEnabled = MutableLiveData<Boolean>().apply {
        value = preferenceManager.isTelemetryEnabled()
    }
    val telemetryEnabled: LiveData<Boolean> = _telemetryEnabled

    fun setTelemetryEnabled(value: Boolean) {
        viewModelScope.launch {
            preferenceManager.setTelemetryEnabled(value)
            _telemetryEnabled.postValue(value)
        }
    }
}
