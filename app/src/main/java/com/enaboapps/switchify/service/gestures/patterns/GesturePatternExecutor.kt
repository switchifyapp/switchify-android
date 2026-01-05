package com.enaboapps.switchify.service.gestures.patterns

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GesturePatternExecutor(private val gesturePattern: GesturePattern) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var executionJob: Job? = null

    fun execute() {
        GestureLockManager.instance.disableLock()
        GesturePatternManager.setExecuting(true)
        executionJob = scope.launch {
            try {
                gesturePattern.gestures.forEach { gestureData ->
                    delay(gestureData.duration() + 1500)
                    gestureData.executeGesture()
                }
            } finally {
                GesturePatternManager.setExecuting(false)
                executionJob = null
            }
        }
    }

    fun stop(): Boolean {
        if (executionJob != null && executionJob?.isActive == true) {
            executionJob?.cancel()
            executionJob = null
            GesturePatternManager.setExecuting(false)
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_gesture_pattern_stopped,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
            return true
        }
        return false
    }
}