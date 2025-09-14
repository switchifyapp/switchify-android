package com.enaboapps.switchify.service.face

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified facial expression state tracking to eliminate duplication across
 * CameraSwitchManager, HeadControlManager, and CameraSettingsScreenModel
 */
@Singleton
class FacialExpressionStateTracker @Inject constructor(
    private val timingManager: FacialExpressionTimingManager
) {

    /**
     * Represents the state of a facial expression
     */
    data class ExpressionState(
        var isActive: Boolean = false,
        var startTime: Long = 0L
    ) {
        /**
         * Gets the duration the expression has been active
         */
        fun getActiveDuration(): Long {
            return if (isActive) {
                SystemClock.elapsedRealtime() - startTime
            } else {
                0L
            }
        }

        /**
         * Checks if the expression has been held long enough to trigger
         */
        fun hasMetHoldRequirement(requiredHoldTime: Long): Boolean {
            return isActive && getActiveDuration() >= requiredHoldTime
        }

        /**
         * Resets the expression state
         */
        fun reset() {
            isActive = false
            startTime = 0L
        }

        /**
         * Activates the expression with current timestamp
         */
        fun activate() {
            isActive = true
            startTime = SystemClock.elapsedRealtime()
        }
    }

    // Thread-safe state storage
    private val expressionStates = mutableMapOf<String, ExpressionState>()
    private val stateLock = Any()

    /**
     * Gets or creates an expression state for the given expression ID
     */
    private fun getOrCreateState(expressionId: String): ExpressionState {
        return synchronized(stateLock) {
            expressionStates.getOrPut(expressionId) { ExpressionState() }
        }
    }

    /**
     * Starts tracking a facial expression
     * 
     * @param expressionId The expression identifier
     * @return true if the expression was started, false if it was already active
     */
    fun startExpression(expressionId: String): Boolean {
        synchronized(stateLock) {
            val state = getOrCreateState(expressionId)
            if (state.isActive) {
                return false // Already active
            }
            
            state.activate()
            return true
        }
    }

    /**
     * Stops tracking a facial expression
     * 
     * @param expressionId The expression identifier
     * @return true if the expression was stopped, false if it wasn't active
     */
    fun stopExpression(expressionId: String): Boolean {
        synchronized(stateLock) {
            val state = expressionStates[expressionId] ?: return false
            if (!state.isActive) {
                return false // Not active
            }
            
            state.reset()
            return true
        }
    }

    /**
     * Checks if a facial expression is currently active
     * 
     * @param expressionId The expression identifier
     * @return true if the expression is active, false otherwise
     */
    fun isExpressionActive(expressionId: String): Boolean {
        synchronized(stateLock) {
            return expressionStates[expressionId]?.isActive ?: false
        }
    }

    /**
     * Gets the duration a facial expression has been active
     * 
     * @param expressionId The expression identifier
     * @return Duration in milliseconds, or 0 if not active
     */
    fun getExpressionActiveDuration(expressionId: String): Long {
        synchronized(stateLock) {
            return expressionStates[expressionId]?.getActiveDuration() ?: 0L
        }
    }

    /**
     * Checks if a facial expression has been held long enough to trigger based on its configured hold time
     * 
     * @param expressionId The expression identifier
     * @return true if the expression has met its hold requirement, false otherwise
     */
    fun hasExpressionMetHoldRequirement(expressionId: String): Boolean {
        val requiredHoldTime = timingManager.getExpressionHoldTime(expressionId)
        synchronized(stateLock) {
            return expressionStates[expressionId]?.hasMetHoldRequirement(requiredHoldTime) ?: false
        }
    }

    /**
     * Checks if a facial expression has been held long enough for a custom hold time
     * 
     * @param expressionId The expression identifier
     * @param customHoldTime Custom hold time in milliseconds
     * @return true if the expression has been held long enough, false otherwise
     */
    fun hasExpressionMetCustomHoldRequirement(expressionId: String, customHoldTime: Long): Boolean {
        synchronized(stateLock) {
            return expressionStates[expressionId]?.hasMetHoldRequirement(customHoldTime) ?: false
        }
    }

    /**
     * Gets the state object for a facial expression (for advanced use cases)
     * 
     * @param expressionId The expression identifier
     * @return The expression state, or null if not found
     */
    fun getExpressionState(expressionId: String): ExpressionState? {
        synchronized(stateLock) {
            return expressionStates[expressionId]
        }
    }

    /**
     * Resets all facial expression states
     */
    fun resetAllExpressions() {
        synchronized(stateLock) {
            expressionStates.values.forEach { it.reset() }
        }
    }

    /**
     * Resets a specific facial expression state
     * 
     * @param expressionId The expression identifier
     */
    fun resetExpression(expressionId: String) {
        synchronized(stateLock) {
            expressionStates[expressionId]?.reset()
        }
    }

    /**
     * Gets all currently active expression IDs
     * 
     * @return Set of active expression IDs
     */
    fun getActiveExpressions(): Set<String> {
        synchronized(stateLock) {
            return expressionStates.filterValues { it.isActive }.keys.toSet()
        }
    }

    /**
     * Checks if any facial expression is currently active
     * 
     * @return true if any expression is active, false otherwise
     */
    fun hasAnyActiveExpression(): Boolean {
        synchronized(stateLock) {
            return expressionStates.values.any { it.isActive }
        }
    }

    /**
     * Gets states for multiple expressions at once (for UI updates)
     * 
     * @param expressionIds The expression identifiers to get states for
     * @return Map of expression ID to its current state
     */
    fun getMultipleExpressionStates(expressionIds: List<String>): Map<String, ExpressionState> {
        synchronized(stateLock) {
            return expressionIds.associateWith { 
                expressionStates[it] ?: ExpressionState() 
            }
        }
    }

    /**
     * Updates multiple expression states atomically (for batch operations)
     * 
     * @param updates Map of expression ID to whether it should be active
     */
    fun updateMultipleExpressions(updates: Map<String, Boolean>) {
        synchronized(stateLock) {
            updates.forEach { (expressionId, shouldBeActive) ->
                if (shouldBeActive) {
                    startExpression(expressionId)
                } else {
                    stopExpression(expressionId)
                }
            }
        }
    }
}