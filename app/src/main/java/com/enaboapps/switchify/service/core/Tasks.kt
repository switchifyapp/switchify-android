package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.service.gestures.AutoScrollManager
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.service.gestures.patterns.GesturePatternManager

/**
 * Singleton class responsible for managing and checking ongoing tasks.
 * This includes auto-scrolling, gesture patterns, and other active processes
 * that may need to be stopped under certain conditions.
 */
class Tasks private constructor() {

    companion object {
        private var instance: Tasks? = null

        /**
         * Gets the singleton instance of Tasks.
         * @return The singleton instance of Tasks.
         */
        fun getInstance(): Tasks {
            return instance ?: synchronized(this) {
                instance ?: Tasks().also { instance = it }
            }
        }
    }

    /**
     * Checks for any ongoing tasks that need to be stopped or advanced.
     * This method is called when the user performs certain actions or when the screen turns off.
     *
     * @return True if any ongoing task was found and potentially stopped/advanced, false otherwise.
     */
    fun stopOngoingTaskForSwitchPress(): Boolean {
        if (GestureRepeatManager.instance.stopRepeatForSwitchPress()) return true

        // Stop auto-scrolling if active
        if (AutoScrollManager.getInstance().stopAutoScroll()) return true

        // If manual progression is enabled, advance to next step instead of stopping
        if (GesturePatternManager.advanceToNextStep()) return true

        // Otherwise, stop gesture pattern if stop setting is enabled
        if (GesturePatternManager.stopCurrentPattern()) return true

        // If neither setting is enabled, still consume the switch event while pattern is running
        if (GesturePatternManager.isGesturePatternActive()) return true

        return false
    }

    fun shouldAbsorbSwitchRelease(): Boolean {
        return hasOngoingTask()
    }

    fun shouldAbsorbSwitchReleaseAfterAction(): Boolean {
        return shouldAbsorbSwitchRelease() ||
                GestureRepeatManager.instance.isAutoRepeatEnabled() ||
                GestureLockManager.instance.isLocked()
    }

    fun hasOngoingTask(): Boolean {
        return GestureRepeatManager.instance.isRepeatSessionActive() ||
                AutoScrollManager.getInstance().isAutoScrolling() ||
                GesturePatternManager.isGesturePatternActive()
    }

    fun checkOngoingTasks(): Boolean = stopOngoingTaskForSwitchPress()
}
