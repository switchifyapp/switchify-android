package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.service.gestures.AutoScrollManager
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.service.gestures.patterns.GesturePatternManager
import com.enaboapps.switchify.pc.PcMouseRepeatManager

class Tasks private constructor() {
    companion object {
        private var instance: Tasks? = null

        fun getInstance(): Tasks {
            return instance ?: synchronized(this) {
                instance ?: Tasks().also { instance = it }
            }
        }
    }

    fun hasActiveStoppableTask(): Boolean {
        return PcMouseRepeatManager.instance.isRepeating() ||
                GestureRepeatManager.instance.isRepeatSessionActive() ||
                AutoScrollManager.getInstance().isAutoScrolling() ||
                GesturePatternManager.isGesturePatternActive()
    }

    fun stopActiveStoppableTask(): Boolean {
        if (PcMouseRepeatManager.instance.stopForSwitchPress()) return true
        if (GestureRepeatManager.instance.stopRepeatForSwitchPress()) return true
        if (AutoScrollManager.getInstance().stopAutoScroll()) return true
        if (GesturePatternManager.advanceToNextStep()) return true
        if (GesturePatternManager.stopCurrentPattern()) return true
        if (GesturePatternManager.isGesturePatternActive()) return true
        return false
    }

    fun stopOngoingTaskForSwitchPress(): Boolean = stopActiveStoppableTask()

    fun checkOngoingTasks(): Boolean = stopActiveStoppableTask()
}
