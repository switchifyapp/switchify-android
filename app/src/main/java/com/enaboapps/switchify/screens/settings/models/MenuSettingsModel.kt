package com.enaboapps.switchify.screens.settings.models

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import kotlinx.coroutines.launch

class MenuSettingsModel(context: Context) : ViewModel() {
    private val preferenceManager = PreferenceManager(context)

    private val _menuTransparency = MutableLiveData<Boolean>().apply {
        value =
            preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_MENU_TRANSPARENCY)
    }
    val menuTransparency: LiveData<Boolean> = _menuTransparency

    private val _menuSizeScale = MutableLiveData<Int>().apply {
        value = preferenceManager.getIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_MENU_SIZE_SCALE,
            100
        )
    }
    val menuSizeScale: LiveData<Int> = _menuSizeScale

    fun setMenuTransparency(value: Boolean) {
        viewModelScope.launch {
            preferenceManager.setBooleanValue(
                PreferenceManager.Keys.PREFERENCE_KEY_MENU_TRANSPARENCY,
                value
            )
            _menuTransparency.postValue(value)
        }
    }

    fun setMenuSizeScale(value: Int) {
        viewModelScope.launch {
            preferenceManager.setIntegerValue(
                PreferenceManager.Keys.PREFERENCE_KEY_MENU_SIZE_SCALE,
                value
            )
            _menuSizeScale.postValue(value)
        }
    }
}
