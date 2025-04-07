package com.enaboapps.switchify.service.gestures.patterns

import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GesturePatternExecutor(private val gesturePattern: GesturePattern) {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun execute() {
        scope.launch {
            gesturePattern.gestures.forEach { gestureData ->
                gestureData.executeGesture()
                delay(gestureData.duration() + 5)
            }
        }
    }
}