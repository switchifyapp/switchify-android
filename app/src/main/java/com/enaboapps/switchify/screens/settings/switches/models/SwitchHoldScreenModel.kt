package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.switches.SwitchHoldPolicy

class SwitchHoldScreenModel(context: Context) : ViewModel() {
    private val preferenceManager = PreferenceManager(context)

    private val _switchHoldEnabled = MutableLiveData(
        SwitchHoldPolicy.isEnabled(preferenceManager)
    )
    val switchHoldEnabled: LiveData<Boolean> = _switchHoldEnabled

    private val _switchHoldTime = MutableLiveData(
        preferenceManager.getLongValue(PreferenceManager.PREFERENCE_KEY_SWITCH_HOLD_TIME)
    )
    val switchHoldTime: LiveData<Long> = _switchHoldTime

    fun setSwitchHoldEnabled(value: Boolean) {
        preferenceManager.setBooleanValue(
            PreferenceManager.PREFERENCE_KEY_SWITCH_HOLD_ENABLED,
            value
        )
        _switchHoldEnabled.value = value
    }

    fun setSwitchHoldTime(value: Long) {
        preferenceManager.setLongValue(PreferenceManager.PREFERENCE_KEY_SWITCH_HOLD_TIME, value)
        _switchHoldTime.value = value
    }
}
