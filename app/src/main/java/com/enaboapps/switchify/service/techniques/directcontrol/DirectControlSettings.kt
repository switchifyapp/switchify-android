package com.enaboapps.switchify.service.techniques.directcontrol

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager

class DirectControlSettings(context: Context) {
    private val prefs = PreferenceManager(context)

    fun baseStep(): Int = prefs.getIntegerValue(KEY_BASE_STEP, 20)
    fun maxStep(): Int = prefs.getIntegerValue(KEY_MAX_STEP, 80)
    fun accelIncrement(): Int = prefs.getIntegerValue(KEY_ACCEL_INCREMENT, 5)
    fun accelWindowMs(): Long = prefs.getLongValue(KEY_ACCEL_WINDOW_MS, 150)
    fun decayWindowMs(): Long = prefs.getLongValue(KEY_DECAY_WINDOW_MS, 300)
    fun precisionEnabled(): Boolean = prefs.getBooleanValue(KEY_PRECISION_ENABLED, false)
    fun precisionMultiplier(): Float = prefs.getFloatValue(KEY_PRECISION_MULTIPLIER, 0.5f)

    companion object {
        const val KEY_BASE_STEP = "direct_control_base_step"
        const val KEY_MAX_STEP = "direct_control_max_step"
        const val KEY_ACCEL_INCREMENT = "direct_control_accel_increment"
        const val KEY_ACCEL_WINDOW_MS = "direct_control_accel_window_ms"
        const val KEY_DECAY_WINDOW_MS = "direct_control_decay_window_ms"
        const val KEY_PRECISION_ENABLED = "direct_control_precision_enabled"
        const val KEY_PRECISION_MULTIPLIER = "direct_control_precision_multiplier"
    }
}

