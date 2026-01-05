package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.service.gestures.AutoScrollManager
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
     * Checks for any ongoing tasks that need to be stopped.
     * This method is called when the user performs certain actions or when the screen turns off.
     *
     * @return True if any ongoing task was found and potentially stopped, false otherwise.
     */
    fun checkOngoingTasks(): Boolean {
        // Stop auto-scrolling if active
        if (AutoScrollManager.getInstance().stopAutoScroll()) return true

        // Stop gesture pattern if enabled, otherwise just check if one is active
        if (GesturePatternManager.stopCurrentPattern()) return true

        // If stop setting is disabled, still consume the switch event while pattern is running
        if (GesturePatternManager.isGesturePatternActive()) return true

        return false
    }
}