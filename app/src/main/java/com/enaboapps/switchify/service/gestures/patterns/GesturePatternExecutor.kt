package com.enaboapps.switchify.service.gestures.patterns

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class GesturePatternExecutor(
    private val gesturePattern: GesturePattern,
    private val context: Context
) {
    // Own a cancellable scope with SupervisorJob to prevent child failures from affecting parent
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var executionJob: Job? = null
    private val preferenceManager = PreferenceManager(context)

    // Manual mode state
    private val currentStepIndex = AtomicInteger(-1)
    @Volatile
    private var isManualMode = false
    @Volatile
    private var isCleanedUp = false

    private fun isManualProgressionEnabled(): Boolean {
        return preferenceManager.getBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_GESTURE_PATTERN_MANUAL_PROGRESSION
        )
    }

    fun execute() {
        if (isCleanedUp) return // Prevent execution on cleaned up executor

        GestureLockManager.instance.disableLock()
        GesturePatternManager.registerExecutor(this)
        isManualMode = isManualProgressionEnabled()
        currentStepIndex.set(-1)

        if (isManualMode) {
            executeManualMode()
        } else {
            executeAutomaticMode()
        }
    }

    private fun executeAutomaticMode() {
        executionJob = scope.launch {
            try {
                gesturePattern.gestures.forEach { gestureData ->
                    delay(gestureData.duration() + 1500)
                    gestureData.executeGesture()
                }
            } finally {
                cleanup()
            }
        }
    }

    private fun executeManualMode() {
        // Show initial message
        ServiceMessageHUD.instance.showMessage(
            R.string.hud_gesture_pattern_manual_mode_started,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )

        // Execute first step immediately
        if (gesturePattern.gestures.isNotEmpty()) {
            executeNextStep()
        } else {
            cleanup()
        }
    }

    private fun executeNextStep() {
        if (isCleanedUp) return

        val nextIndex = currentStepIndex.incrementAndGet()
        if (nextIndex >= gesturePattern.gestures.size) {
            finishPattern()
            return
        }

        scope.launch {
            try {
                val gesture = gesturePattern.gestures[nextIndex]
                gesture.executeGesture()

                // Show progress message
                val remaining = gesturePattern.gestures.size - nextIndex - 1
                if (remaining > 0) {
                    ServiceMessageHUD.instance.showMessage(
                        R.string.hud_gesture_pattern_step_completed,
                        arrayOf(nextIndex + 1, gesturePattern.gestures.size, remaining),
                        ServiceMessageHUD.MessageType.PERMANENT
                    )
                }
            } catch (e: Exception) {
                // Handle execution errors gracefully
                cleanup()
            }
        }
    }

    fun advanceToNextStep(): Boolean {
        if (!isManualMode) return false
        if (isCleanedUp) return false

        val nextIndex = currentStepIndex.get() + 1
        if (nextIndex >= gesturePattern.gestures.size) {
            finishPattern()
            return true
        }

        executeNextStep()
        return true
    }

    private fun finishPattern() {
        ServiceMessageHUD.instance.showMessage(
            R.string.hud_gesture_pattern_completed,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )
        cleanup()
    }

    fun stop(): Boolean {
        if (isCleanedUp) return false

        if (executionJob != null && executionJob?.isActive == true) {
            executionJob?.cancel()
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_gesture_pattern_stopped,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
            cleanup()
            return true
        }

        // Manual mode doesn't have a job, but still needs to be stopped
        if (isManualMode && currentStepIndex.get() >= 0 && currentStepIndex.get() < gesturePattern.gestures.size) {
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_gesture_pattern_stopped,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
            cleanup()
            return true
        }

        return false
    }

    private fun cleanup() {
        if (isCleanedUp) return
        isCleanedUp = true

        executionJob = null
        GesturePatternManager.unregisterExecutor(this)

        // Cancel the scope to prevent coroutine leaks
        scope.cancel()
    }
}