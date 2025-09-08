package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager

class HeadControlSettings(context: Context) {
    private val prefs = PreferenceManager(context.applicationContext)

    fun sensitivity(): Float = prefs.getFloatValue(KEY_SENSITIVITY, 0.8f).coerceIn(0.1f, 2.0f)
    
    fun deadzone(): Float = prefs.getFloatValue(KEY_DEADZONE, 0.5f).coerceIn(0.1f, 10.0f)
    
    fun baseStep(): Int = prefs.getIntegerValue(KEY_BASE_STEP, 25).coerceIn(5, 100)

    companion object {
        const val KEY_SENSITIVITY = "head_control_sensitivity"
        const val KEY_DEADZONE = "head_control_deadzone"
        const val KEY_BASE_STEP = "head_control_base_step"
    }
}