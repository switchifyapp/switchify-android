package com.enaboapps.switchify.service.techniques.directcontrol

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager

class DirectControlSettings(context: Context) {
    private val prefs = PreferenceManager(context)

    // Simplified: single speed level (1–5) maps to movement tuning
    fun speedLevel(): Int = prefs.getIntegerValue(KEY_SPEED_LEVEL, 3).coerceIn(1, 5)

    fun baseStep(): Int = when (speedLevel()) {
        1 -> 10
        2 -> 15
        3 -> 20
        4 -> 25
        else -> 30
    }

    fun maxStep(): Int = when (speedLevel()) {
        1 -> 30
        2 -> 50
        3 -> 80
        4 -> 110
        else -> 140
    }

    fun accelIncrement(): Int = when (speedLevel()) {
        1 -> 2
        2 -> 3
        3 -> 5
        4 -> 7
        else -> 9
    }

    fun accelWindowMs(): Long = 150
    fun decayWindowMs(): Long = 300
    fun precisionEnabled(): Boolean = prefs.getBooleanValue(KEY_PRECISION_ENABLED, false)
    fun precisionMultiplier(): Float = 0.5f
    fun repeatDelay(): Long = prefs.getLongValue(KEY_REPEAT_DELAY, 100L).coerceIn(25L, 1000L)

    companion object {
        // New simplified setting
        const val KEY_SPEED_LEVEL = "direct_control_speed_level"
        const val KEY_REPEAT_DELAY = "direct_control_repeat_delay"

        // Legacy keys retained for backward compatibility (no longer used by UI)
        const val KEY_BASE_STEP = "direct_control_base_step"
        const val KEY_MAX_STEP = "direct_control_max_step"
        const val KEY_ACCEL_INCREMENT = "direct_control_accel_increment"
        const val KEY_ACCEL_WINDOW_MS = "direct_control_accel_window_ms"
        const val KEY_DECAY_WINDOW_MS = "direct_control_decay_window_ms"
        const val KEY_PRECISION_ENABLED = "direct_control_precision_enabled"
        const val KEY_PRECISION_MULTIPLIER = "direct_control_precision_multiplier"
    }
}
