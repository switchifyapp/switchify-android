package com.enaboapps.switchify.screens.settings.models

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.utils.SentryReporter
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
            SentryReporter.setEnabled(value)
            _telemetryEnabled.postValue(value)
        }
    }
}
