package com.enaboapps.switchify.service.gestures.patterns

import java.util.concurrent.atomic.AtomicBoolean

object GesturePatternManager {
    private val isExecuting = AtomicBoolean(false)

    fun setExecuting(executing: Boolean) {
        isExecuting.set(executing)
    }

    fun isGesturePatternActive(): Boolean {
        return isExecuting.get()
    }
}