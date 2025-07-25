package com.enaboapps.switchify.screens.settings.models

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import kotlinx.coroutines.launch

class AISettingsModel(context: Context) : ViewModel() {
    private val preferenceManager = PreferenceManager(context)

    private val _aiSuggestionsEnabled = MutableLiveData<Boolean>().apply {
        value =
            preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_AI_SUGGESTIONS_ENABLED)
    }
    val aiSuggestionsEnabled: LiveData<Boolean> = _aiSuggestionsEnabled

    private val _aiVisualAnalysisEnabled = MutableLiveData<Boolean>().apply {
        value =
            preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_AI_VISUAL_ANALYSIS_ENABLED, true)
    }
    val aiVisualAnalysisEnabled: LiveData<Boolean> = _aiVisualAnalysisEnabled

    fun setAiSuggestionsEnabled(value: Boolean) {
        viewModelScope.launch {
            preferenceManager.setBooleanValue(
                PreferenceManager.Keys.PREFERENCE_KEY_AI_SUGGESTIONS_ENABLED,
                value
            )
            _aiSuggestionsEnabled.postValue(value)
        }
    }

    fun setAiVisualAnalysisEnabled(value: Boolean) {
        viewModelScope.launch {
            preferenceManager.setBooleanValue(
                PreferenceManager.Keys.PREFERENCE_KEY_AI_VISUAL_ANALYSIS_ENABLED,
                value
            )
            _aiVisualAnalysisEnabled.postValue(value)
        }
    }
}