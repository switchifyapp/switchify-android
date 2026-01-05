package com.enaboapps.switchify.service.gestures.patterns

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import java.util.concurrent.atomic.AtomicBoolean

object GesturePatternManager {
    private val isExecuting = AtomicBoolean(false)
    private var currentExecutor: GesturePatternExecutor? = null
    private lateinit var preferenceManager: PreferenceManager

    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
    }

    fun setExecuting(executing: Boolean) {
        isExecuting.set(executing)
        if (!executing) {
            currentExecutor = null
        }
    }

    fun isGesturePatternActive(): Boolean {
        return isExecuting.get()
    }

    fun setCurrentExecutor(executor: GesturePatternExecutor?) {
        currentExecutor = executor
    }

    private fun isStopOnSwitchEnabled(): Boolean {
        return if (::preferenceManager.isInitialized) {
            preferenceManager.getBooleanValue(
                PreferenceManager.Keys.PREFERENCE_KEY_STOP_PATTERN_ON_SWITCH
            )
        } else {
            false
        }
    }

    fun stopCurrentPattern(): Boolean {
        if (!isStopOnSwitchEnabled()) return false
        return currentExecutor?.stop() ?: false
    }
}