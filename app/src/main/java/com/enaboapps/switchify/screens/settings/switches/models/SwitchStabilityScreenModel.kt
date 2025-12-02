package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.enaboapps.switchify.backend.preferences.PreferenceManager

class SwitchStabilityScreenModel(context: Context) : ViewModel() {
    private val preferenceManager = PreferenceManager(context)

    private val _switchIgnoreRepeat = MutableLiveData<Boolean>().apply {
        value =
            preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_SWITCH_IGNORE_REPEAT)
    }
    val switchIgnoreRepeat: LiveData<Boolean> = _switchIgnoreRepeat

    private val _switchIgnoreRepeatDelay = MutableLiveData<Long>().apply {
        value =
            preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_SWITCH_IGNORE_REPEAT_DELAY)
    }
    val switchIgnoreRepeatDelay: LiveData<Long> = _switchIgnoreRepeatDelay

    private val _switchHoldTime = MutableLiveData<Long>().apply {
        value =
            preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_SWITCH_HOLD_TIME)
    }
    val switchHoldTime: LiveData<Long> = _switchHoldTime


    fun setSwitchIgnoreRepeat(value: Boolean) {
        preferenceManager.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_SWITCH_IGNORE_REPEAT,
            value
        )
        _switchIgnoreRepeat.postValue(value)
    }

    fun setSwitchIgnoreRepeatDelay(value: Long) {
        preferenceManager.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_SWITCH_IGNORE_REPEAT_DELAY,
            value
        )
        _switchIgnoreRepeatDelay.postValue(value)
    }

    fun setSwitchHoldTime(value: Long) {
        preferenceManager.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_SWITCH_HOLD_TIME,
            value
        )
        _switchHoldTime.postValue(value)
    }
}