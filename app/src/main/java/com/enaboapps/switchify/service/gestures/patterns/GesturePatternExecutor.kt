package com.enaboapps.switchify.service.gestures.patterns

import com.enaboapps.switchify.R
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

class GesturePatternExecutor(private val gesturePattern: GesturePattern) {
    // Own a cancellable scope with SupervisorJob to prevent child failures from affecting parent
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var executionJob: Job? = null
    @Volatile
    private var isCleanedUp = false

    fun execute() {
        if (isCleanedUp) return // Prevent execution on cleaned up executor

        GestureLockManager.instance.disableLock()
        GesturePatternManager.registerExecutor(this)
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