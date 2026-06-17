package com.enaboapps.switchify.service.gestures.patterns

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
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
    companion object {
        private const val TAG = "GesturePatternExecutor"
    }

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

        GestureLockManager.instance.disableLock(allowAutoReenable = false)
        GesturePatternManager.registerExecutor(this)
        isManualMode = isManualProgressionEnabled()
        currentStepIndex.set(-1)
        Logger.log(
            LogEvent.GesturePatternExecutionStarted,
            data = mapOf(
                "result" to "started",
                "pattern_id" to gesturePattern.id,
                "steps" to gesturePattern.gestures.size,
                "mode" to if (isManualMode) "manual" else "automatic"
            )
        )

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
            ServiceMessageHUD.MessageType.DISAPPEARING,
            severity = MessageSeverity.Info
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

        executeStepAtIndex(nextIndex)
    }

    private fun executeStepAtIndex(stepIndex: Int) {
        if (isCleanedUp) return

        scope.launch {
            try {
                val gesture = gesturePattern.gestures[stepIndex]
                gesture.executeGesture()

                // Show progress message or finish if this was the last step
                val remaining = gesturePattern.gestures.size - stepIndex - 1
                if (remaining > 0) {
                    ServiceMessageHUD.instance.showMessage(
                        R.string.hud_gesture_pattern_step_completed,
                        arrayOf(stepIndex + 1, gesturePattern.gestures.size, remaining),
                        ServiceMessageHUD.MessageType.PERMANENT,
                        severity = MessageSeverity.Success
                    )
                } else {
                    // Last step completed, finish the pattern
                    finishPattern()
                }
            } catch (e: Exception) {
                // Log the error
                Log.e(TAG, "Error executing gesture pattern step", e)
                Logger.log(
                    LogEvent.GesturePatternExecutionFailed,
                    data = mapOf(
                        "result" to "failure",
                        "reason" to "step_execution_exception",
                        "pattern_id" to gesturePattern.id,
                        "step_index" to stepIndex
                    ),
                    throwable = e
                )

                // Show user-facing error message
                ServiceMessageHUD.instance.showMessage(
                    R.string.hud_gesture_pattern_error,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    severity = MessageSeverity.Error
                )

                cleanup()
            }
        }
    }

    fun advanceToNextStep(): Boolean {
        if (!isManualMode) return false
        if (isCleanedUp) return false

        // Atomically increment and get the new index
        val nextIndex = currentStepIndex.incrementAndGet()

        // Check bounds after increment
        if (nextIndex >= gesturePattern.gestures.size) {
            finishPattern()
            return true
        }

        // Execute at the already-incremented index
        executeStepAtIndex(nextIndex)
        return true
    }

    private fun finishPattern() {
        Logger.log(
            LogEvent.GesturePatternExecutionCompleted,
            data = mapOf(
                "result" to "success",
                "pattern_id" to gesturePattern.id,
                "steps" to gesturePattern.gestures.size,
                "mode" to if (isManualMode) "manual" else "automatic"
            )
        )
        ServiceMessageHUD.instance.showMessage(
            R.string.hud_gesture_pattern_completed,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            severity = MessageSeverity.Success
        )
        cleanup()
    }

    fun stop(): Boolean {
        if (isCleanedUp) return false

        if (executionJob != null && executionJob?.isActive == true) {
            executionJob?.cancel()
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_gesture_pattern_stopped,
                ServiceMessageHUD.MessageType.DISAPPEARING,
                severity = MessageSeverity.Warning
            )
            cleanup()
            return true
        }

        // Manual mode doesn't have a job, but still needs to be stopped
        if (isManualMode && currentStepIndex.get() >= 0 && currentStepIndex.get() < gesturePattern.gestures.size) {
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_gesture_pattern_stopped,
                ServiceMessageHUD.MessageType.DISAPPEARING,
                severity = MessageSeverity.Warning
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
