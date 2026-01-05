package com.enaboapps.switchify.service.gestures.patterns

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object GesturePatternManager {
    private val activeExecutors = ConcurrentHashMap<Int, GesturePatternExecutor>()
    private val executorIdCounter = AtomicInteger(0)
    private lateinit var preferenceManager: PreferenceManager
    private val lock = Any()

    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
    }

    fun registerExecutor(executor: GesturePatternExecutor) {
        synchronized(lock) {
            val id = executorIdCounter.incrementAndGet()
            activeExecutors[id] = executor
        }
    }

    fun unregisterExecutor(executor: GesturePatternExecutor) {
        synchronized(lock) {
            // Remove by value instead of key
            val iterator = activeExecutors.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value === executor) {
                    iterator.remove()
                    break
                }
            }
        }
    }

    fun isGesturePatternActive(): Boolean {
        synchronized(lock) {
            return activeExecutors.isNotEmpty()
        }
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

        synchronized(lock) {
            if (activeExecutors.isEmpty()) return false

            // Stop all active executors (don't short-circuit with any)
            var anyStopped = false
            activeExecutors.values.forEach { executor ->
                if (executor.stop()) {
                    anyStopped = true
                }
            }
            return anyStopped
        }
    }

    fun advanceToNextStep(): Boolean {
        synchronized(lock) {
            if (activeExecutors.isEmpty()) return false

            // Advance all active executors (don't short-circuit with any)
            var anyAdvanced = false
            activeExecutors.values.forEach { executor ->
                if (executor.advanceToNextStep()) {
                    anyAdvanced = true
                }
            }
            return anyAdvanced
        }
    }
}