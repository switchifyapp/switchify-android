package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.service.gestures.AutoScrollManager
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.service.gestures.patterns.GesturePatternManager
import com.enaboapps.switchify.switches.SwitchAction

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
        return stopOngoingTask()
    }

    fun shouldBypassOngoingTaskStop(action: SwitchAction): Boolean {
        return when (action.id) {
            SwitchAction.ACTION_CHANGE_SCANNING_DIRECTION,
            SwitchAction.ACTION_MOVE_TO_NEXT_ITEM,
            SwitchAction.ACTION_MOVE_TO_PREVIOUS_ITEM -> true
            else -> false
        }
    }

    fun stopOngoingTaskForSwitchAction(action: SwitchAction): Boolean {
        if (shouldBypassOngoingTaskStop(action)) return false
        return stopOngoingTask()
    }

    private fun stopOngoingTask(): Boolean {
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

    fun shouldAbsorbSwitchReleaseForAction(action: SwitchAction): Boolean {
        if (shouldBypassOngoingTaskStop(action)) return false
        return shouldAbsorbSwitchRelease()
    }

    fun shouldAbsorbSwitchReleaseAfterAction(): Boolean {
        return shouldAbsorbSwitchRelease()
    }

    fun hasOngoingTask(): Boolean {
        return GestureRepeatManager.instance.isRepeatSessionActive() ||
                GestureLockManager.instance.isGestureLockEngaged() ||
                AutoScrollManager.getInstance().isAutoScrolling() ||
                GesturePatternManager.isGesturePatternActive()
    }

    fun checkOngoingTasks(): Boolean = stopOngoingTaskForSwitchPress()
}
