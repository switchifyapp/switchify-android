package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager

class HeadControlSettings(context: Context) {
    private val prefs = PreferenceManager(context)

    fun isEnabled(): Boolean = prefs.getBooleanValue(KEY_ENABLED, false)
    
    fun sensitivity(): Float = prefs.getFloatValue(KEY_SENSITIVITY, 0.8f).coerceIn(0.1f, 2.0f)
    
    fun deadzone(): Float = prefs.getFloatValue(KEY_DEADZONE, 2.0f).coerceIn(0.5f, 10.0f)
    
    fun baseStep(): Int = prefs.getIntegerValue(KEY_BASE_STEP, 25).coerceIn(5, 100)
    
    fun smoothingEnabled(): Boolean = prefs.getBooleanValue(KEY_SMOOTHING_ENABLED, true)
    
    fun autoActivateInMenus(): Boolean = prefs.getBooleanValue(KEY_AUTO_ACTIVATE_IN_MENUS, true)

    companion object {
        const val KEY_ENABLED = "head_control_enabled"
        const val KEY_SENSITIVITY = "head_control_sensitivity"
        const val KEY_DEADZONE = "head_control_deadzone"
        const val KEY_BASE_STEP = "head_control_base_step"
        const val KEY_SMOOTHING_ENABLED = "head_control_smoothing_enabled"
        const val KEY_AUTO_ACTIVATE_IN_MENUS = "head_control_auto_activate_in_menus"
    }
}