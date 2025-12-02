package com.enaboapps.switchify.screens.settings.pause.models

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.enaboapps.switchify.backend.preferences.PreferenceManager

class PauseSettingsScreenModel(context: Context) : ViewModel() {
    private val preferenceManager = PreferenceManager(context)

    private val _holdToUnpauseDuration = MutableLiveData<Long>().apply {
        value =
            preferenceManager.getLongValue(
                PreferenceManager.Keys.PREFERENCE_KEY_HOLD_TO_UNPAUSE_DURATION,
                2000L
            )
    }
    val holdToUnpauseDuration: LiveData<Long> = _holdToUnpauseDuration

    private val _pauseTimeout = MutableLiveData<Long>().apply {
        value =
            preferenceManager.getLongValue(
                PreferenceManager.Keys.PREFERENCE_KEY_PAUSE_TIMEOUT,
                30000L
            )
    }
    val pauseTimeout: LiveData<Long> = _pauseTimeout

    fun setHoldToUnpauseDuration(value: Long) {
        preferenceManager.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_HOLD_TO_UNPAUSE_DURATION,
            value
        )
        _holdToUnpauseDuration.postValue(value)
    }

    fun setPauseTimeout(value: Long) {
        preferenceManager.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_PAUSE_TIMEOUT,
            value
        )
        _pauseTimeout.postValue(value)
    }
}
