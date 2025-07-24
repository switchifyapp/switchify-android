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

    private val _menuRowsPerPage = MutableLiveData<Int>().apply {
        value = preferenceManager.getIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_MENU_ROWS_PER_PAGE,
            2
        )
    }
    val menuRowsPerPage: LiveData<Int> = _menuRowsPerPage

    fun setMenuTransparency(value: Boolean) {
        viewModelScope.launch {
            preferenceManager.setBooleanValue(
                PreferenceManager.Keys.PREFERENCE_KEY_MENU_TRANSPARENCY,
                value
            )
            _menuTransparency.postValue(value)
        }
    }

    fun setMenuRowsPerPage(value: Int) {
        viewModelScope.launch {
            preferenceManager.setIntegerValue(
                PreferenceManager.Keys.PREFERENCE_KEY_MENU_ROWS_PER_PAGE,
                value
            )
            _menuRowsPerPage.postValue(value)
        }
    }
}