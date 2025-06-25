package com.enaboapps.switchify.service.gestures.patterns

import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GesturePatternExecutor(private val gesturePattern: GesturePattern) {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun execute() {
        GestureLockManager.instance.disableLock()
        GesturePatternManager.setExecuting(true)
        scope.launch {
            try {
                gesturePattern.gestures.forEach { gestureData ->
                    delay(gestureData.duration() + 1500)
                    gestureData.executeGesture()
                }
            } finally {
                GesturePatternManager.setExecuting(false)
            }
        }
    }
}