package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import android.util.Log
import com.enaboapps.switchify.service.gestures.data.GestureType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Unified state manager for all gesture-related operations.
 * Consolidates state tracking across gesture execution, auto-select, and visual feedback.
 */
object GestureStateManager {
    
    private const val TAG = "GestureStateManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State listeners for cross-component communication
    private val stateListeners = ConcurrentHashMap<String, GestureStateListener>()
    
    // Core gesture execution state
    private val isPerformingGesture = AtomicBoolean(false)
    private val currentGestureType = AtomicReference<GestureType?>(null)
    private val gestureStartPoint = AtomicReference<PointF?>(null)
    private val lastGestureTime = AtomicLong(0L)
    
    // Auto-select state
    private val autoSelectInProgress = AtomicBoolean(false)
    private val bypassAutoSelect = AtomicBoolean(false)
    private var autoSelectJob: Job? = null
    
    // Selection context state
    private val methodTypeInvokedForStartScanning = AtomicReference<String?>(null)
    
    // Visual feedback coordination state
    private val activeVisualFeedback = AtomicBoolean(false)
    
    private const val GESTURE_DELAY_MS = 500L
    
    // State change event types
    const val EVENT_GESTURE_STARTED = "gesture_started"
    const val EVENT_GESTURE_ENDED = "gesture_ended"
    const val EVENT_AUTO_SELECT_STARTED = "auto_select_started"
    const val EVENT_AUTO_SELECT_CANCELLED = "auto_select_cancelled"
    const val EVENT_AUTO_SELECT_COMPLETED = "auto_select_completed"
    const val EVENT_STATE_RESET = "state_reset"
    
    // Dispatch event types for execution pipeline
    const val EVENT_GESTURE_DISPATCH_STARTED = "gesture_dispatch_started"
    const val EVENT_GESTURE_DISPATCH_COMPLETED = "gesture_dispatch_completed"
    const val EVENT_GESTURE_DISPATCH_CANCELLED = "gesture_dispatch_cancelled"
    const val EVENT_GESTURE_DISPATCH_ERROR = "gesture_dispatch_error"
    
    /**
     * Interface for listening to gesture state changes.
     */
    interface GestureStateListener {
        fun onStateChanged(event: String, data: Map<String, Any> = emptyMap())
    }
    
    /**
     * Registers a state listener for gesture events.
     */
    fun addStateListener(id: String, listener: GestureStateListener) {
        stateListeners[id] = listener
    }
    
    /**
     * Removes a state listener.
     */
    fun removeStateListener(id: String) {
        stateListeners.remove(id)
    }
    
    /**
     * Notifies all listeners of a state change.
     */
    private fun notifyStateChange(event: String, data: Map<String, Any> = emptyMap()) {
        stateListeners.values.forEach { listener ->
            try {
                listener.onStateChanged(event, data)
            } catch (e: Exception) {
                // Log but don't let one listener failure affect others
            }
        }
    }
    
    // === Gesture Execution State Management ===
    
    /**
     * Starts a gesture with the specified type and starting point.
     */
    fun startGesture(type: GestureType, startPoint: PointF): Boolean {
        // Check if enough time has passed since last gesture
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGestureTime.get() < GESTURE_DELAY_MS) {
            return false
        }
        
        // Prevent starting if already performing a gesture
        if (!isPerformingGesture.compareAndSet(false, true)) {
            return false
        }
        
        currentGestureType.set(type)
        gestureStartPoint.set(startPoint)
        lastGestureTime.set(currentTime)
        
        notifyStateChange(EVENT_GESTURE_STARTED, mapOf(
            "type" to type,
            "startPoint" to startPoint
        ))
        
        return true
    }
    
    /**
     * Ends the current gesture.
     */
    fun endGesture(): Boolean {
        if (!isPerformingGesture.compareAndSet(true, false)) {
            return false
        }
        
        val type = currentGestureType.getAndSet(null)
        val startPoint = gestureStartPoint.getAndSet(null)
        
        notifyStateChange(EVENT_GESTURE_ENDED, mapOf(
            "type" to (type ?: "unknown"),
            "startPoint" to (startPoint ?: PointF(0f, 0f))
        ))
        
        return true
    }
    
    /**
     * Cancels the current gesture without completion.
     */
    fun cancelGesture(): Boolean {
        Log.d(TAG, "cancelGesture called")
        Log.d(TAG, "State before cancel: ${getStateSummary()}")
        
        if (!isPerformingGesture.compareAndSet(true, false)) {
            Log.d(TAG, "cancelGesture: no gesture was in progress")
            return false
        }
        
        currentGestureType.set(null)
        gestureStartPoint.set(null)
        
        Log.d(TAG, "cancelGesture: cleared state")
        
        notifyStateChange(EVENT_GESTURE_ENDED, mapOf(
            "cancelled" to true
        ))
        
        return true
    }
    
    /**
     * Checks if a gesture is currently being performed.
     */
    fun isGestureInProgress(): Boolean = isPerformingGesture.get()
    
    /**
     * Gets the current gesture type, if any.
     */
    fun getCurrentGestureType(): GestureType? {
        val type = currentGestureType.get()
        Log.d(TAG, "getCurrentGestureType called - returning: $type")
        return type
    }
    
    /**
     * Gets the current gesture start point, if any.
     */
    fun getCurrentGestureStartPoint(): PointF? {
        val point = gestureStartPoint.get()
        Log.d(TAG, "getCurrentGestureStartPoint called - returning: $point")
        return point
    }
    
    // === Auto-Select State Management ===
    
    /**
     * Starts the auto-select process with the specified delay.
     */
    fun startAutoSelect(delayMs: Long, onComplete: () -> Unit): Boolean {
        // Cancel any existing auto-select
        cancelAutoSelect()
        
        if (!autoSelectInProgress.compareAndSet(false, true)) {
            return false
        }
        
        autoSelectJob = scope.launch {
            try {
                notifyStateChange(EVENT_AUTO_SELECT_STARTED, mapOf("delay" to delayMs))
                
                delay(delayMs)
                
                // Check if still in progress (not cancelled)
                if (autoSelectInProgress.get()) {
                    onComplete()
                    autoSelectInProgress.set(false)
                    notifyStateChange(EVENT_AUTO_SELECT_COMPLETED)
                }
            } catch (e: Exception) {
                autoSelectInProgress.set(false)
            }
        }
        
        return true
    }
    
    /**
     * Cancels the auto-select process.
     */
    fun cancelAutoSelect(): Boolean {
        autoSelectJob?.cancel()
        autoSelectJob = null
        
        return if (autoSelectInProgress.compareAndSet(true, false)) {
            notifyStateChange(EVENT_AUTO_SELECT_CANCELLED)
            true
        } else {
            false
        }
    }
    
    /**
     * Checks if auto-select is currently in progress.
     */
    fun isAutoSelectInProgress(): Boolean = autoSelectInProgress.get()
    
    /**
     * Sets the bypass auto-select flag.
     */
    fun setBypassAutoSelect(bypass: Boolean) {
        bypassAutoSelect.set(bypass)
    }
    
    /**
     * Checks if auto-select should be bypassed.
     */
    fun shouldBypassAutoSelect(): Boolean = bypassAutoSelect.get()
    
    // === Selection Context State Management ===
    
    /**
     * Sets the method type that invoked start scanning action.
     */
    fun setMethodTypeForStartScanning(methodType: String?) {
        methodTypeInvokedForStartScanning.set(methodType)
    }
    
    /**
     * Gets the method type that invoked start scanning action.
     */
    fun getMethodTypeForStartScanning(): String? = methodTypeInvokedForStartScanning.get()
    
    // === Visual Feedback State Management ===
    
    /**
     * Sets the active visual feedback state.
     */
    fun setActiveVisualFeedback(active: Boolean) {
        activeVisualFeedback.set(active)
    }
    
    /**
     * Checks if visual feedback is currently active.
     */
    fun isVisualFeedbackActive(): Boolean = activeVisualFeedback.get()
    
    // === Lifecycle Management ===
    
    /**
     * Resets all state to initial values.
     */
    fun resetAllState() {
        Log.d(TAG, "resetAllState called")
        Log.d(TAG, "State before reset: ${getStateSummary()}")
        
        // Cancel any ongoing operations
        cancelAutoSelect()
        
        // Reset all atomic states
        isPerformingGesture.set(false)
        currentGestureType.set(null)
        gestureStartPoint.set(null)
        autoSelectInProgress.set(false)
        bypassAutoSelect.set(false)
        methodTypeInvokedForStartScanning.set(null)
        activeVisualFeedback.set(false)
        
        Log.d(TAG, "resetAllState: all state cleared")
        
        notifyStateChange(EVENT_STATE_RESET)
    }
    
    /**
     * Gets current state summary for debugging.
     */
    fun getStateSummary(): Map<String, Any> {
        return mapOf(
            "isPerformingGesture" to isPerformingGesture.get(),
            "currentGestureType" to (currentGestureType.get()?.name ?: "none"),
            "isAutoSelectInProgress" to autoSelectInProgress.get(),
            "bypassAutoSelect" to bypassAutoSelect.get(),
            "activeVisualFeedback" to activeVisualFeedback.get(),
            "lastGestureTime" to lastGestureTime.get(),
            "listenerCount" to stateListeners.size
        )
    }
    
    // === Dispatch Event Management ===
    
    /**
     * Notifies listeners that a gesture dispatch has started.
     */
    fun notifyGestureDispatchStarted(gestureType: GestureType) {
        notifyStateChange(EVENT_GESTURE_DISPATCH_STARTED, mapOf("gestureType" to gestureType))
    }
    
    /**
     * Notifies listeners that a gesture dispatch has completed successfully.
     */
    fun notifyGestureDispatchCompleted(gestureType: GestureType) {
        notifyStateChange(EVENT_GESTURE_DISPATCH_COMPLETED, mapOf("gestureType" to gestureType))
    }
    
    /**
     * Notifies listeners that a gesture dispatch was cancelled.
     */
    fun notifyGestureDispatchCancelled(gestureType: GestureType) {
        notifyStateChange(EVENT_GESTURE_DISPATCH_CANCELLED, mapOf("gestureType" to gestureType))
    }
    
    /**
     * Notifies listeners that a gesture dispatch encountered an error.
     */
    fun notifyGestureDispatchError(gestureType: GestureType, error: Throwable) {
        notifyStateChange(EVENT_GESTURE_DISPATCH_ERROR, mapOf(
            "gestureType" to gestureType,
            "error" to error
        ))
    }
    
    /**
     * Cleanup resources when shutting down.
     */
    fun cleanup() {
        resetAllState()
        stateListeners.clear()
        scope.cancel()
    }
}