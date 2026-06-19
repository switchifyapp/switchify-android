package com.enaboapps.switchify.service.gestures.execution

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GestureCaptureRouter
import com.enaboapps.switchify.service.gestures.GesturePatternRecorder
import com.enaboapps.switchify.service.gestures.GestureStateManager
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger

/**
 * Central nervous system for all gesture execution in the Switchify accessibility service.
 *
 * Architecture & Design:
 * This class implements the Command pattern to encapsulate gesture execution requests,
 * providing a unified interface that abstracts the complexity of Android's accessibility
 * gesture system while adding comprehensive error handling, state management, and
 * pattern recording functionality.
 *
 * Key Responsibilities:
 * 1. Gesture Execution: Interfaces with Android AccessibilityService.dispatchGesture()
 * 2. Error Handling: Comprehensive try-catch with callback-based error reporting
 * 3. State Integration: Coordinates with GestureStateManager for system-wide state
 * 4. Pattern Recording: Integrates GesturePatternRecorder for learning user patterns
 * 5. Gesture Locking: Manages GestureLockManager for gesture repeat functionality
 * 6. Result Callbacks: Provides flexible callback system for completion/cancellation
 *
 * Integration Points:
 * - GestureStateManager: State coordination and event broadcasting
 * - GesturePatternRecorder: Automatic pattern learning from executed gestures
 * - GestureLockManager: Gesture repeat and lock functionality
 * - SwitchifyAccessibilityService: Android accessibility API interface
 *
 * Thread Safety:
 * - All operations are main-thread safe as required by Android accessibility APIs
 * - Callback execution is wrapped in exception handlers to prevent cascade failures
 * - State updates are atomic through GestureStateManager's thread-safe operations
 *
 * Error Recovery:
 * - Graceful degradation when gesture dispatch fails
 * - Automatic state cleanup on errors to prevent system locks
 * - Comprehensive logging for debugging gesture execution issues
 */
class GestureDispatcher(
    private val accessibilityService: SwitchifyAccessibilityService
) {

    /**
     * Callback interface for handling gesture execution results.
     *
     * This interface provides a clean abstraction for gesture result handling,
     * allowing different components to respond to gesture outcomes without
     * tight coupling to the dispatch implementation.
     *
     * Design Benefits:
     * - Decouples result handling from dispatch logic
     * - Enables custom behavior for different gesture contexts
     * - Supports testing through mock implementations
     * - Provides consistent error handling patterns
     */
    interface GestureResultHandler {
        /**
         * Called when the gesture completes successfully.
         * @param gestureType The type of gesture that completed
         */
        fun onGestureCompleted(gestureType: GestureType)

        /**
         * Called when the gesture is cancelled before completion.
         * @param gestureType The type of gesture that was cancelled
         */
        fun onGestureCancelled(gestureType: GestureType)

        /**
         * Called when the gesture encounters an execution error.
         * @param gestureType The type of gesture that failed
         * @param error The exception that caused the failure
         */
        fun onGestureError(gestureType: GestureType, error: Throwable)
    }

    /**
     * Standard result handler that integrates seamlessly with the unified state management system.
     *
     * This implementation serves as the default behavior for gesture result handling,
     * ensuring consistent state updates and event broadcasting across the entire
     * gesture system without requiring custom handler implementation.
     *
     * Responsibilities:
     * - Broadcasts completion events to all registered state listeners
     * - Maintains system-wide gesture state consistency
     * - Provides centralized error logging for debugging
     * - Enables cross-component coordination through event system
     *
     * State Integration:
     * All state updates go through GestureStateManager to ensure:
     * - Thread-safe atomic operations
     * - Consistent event broadcasting to all listeners
     * - Proper cleanup of gesture-related state
     * - Coordination with visual feedback and other subsystems
     */
    private class DefaultResultHandler(private val gestureType: GestureType) :
        GestureResultHandler {

        override fun onGestureCompleted(gestureType: GestureType) {
            // Update state through GestureStateManager
            GestureStateManager.notifyGestureDispatchCompleted(gestureType)
        }

        override fun onGestureCancelled(gestureType: GestureType) {
            // Update state through GestureStateManager
            GestureStateManager.notifyGestureDispatchCancelled(gestureType)
        }

        override fun onGestureError(gestureType: GestureType, error: Throwable) {
            // Log error and update state
            android.util.Log.e(
                "GestureDispatcher",
                "Gesture dispatch error for $gestureType",
                error
            )
            GestureStateManager.notifyGestureDispatchError(gestureType, error)
        }
    }

    /**
     * Core dispatch method that orchestrates the complete gesture execution pipeline.
     *
     * This method represents the culmination of the gesture system's unified architecture,
     * integrating all major subsystems into a single, coherent execution flow.
     *
     * Execution Pipeline:
     * 1. Handler Resolution: Uses custom handler or default system integration
     * 2. Pattern Recording: Automatically records gesture for learning algorithms
     * 3. Gesture Locking: Stores gesture data for repeat functionality
     * 4. State Broadcasting: Notifies all listeners of dispatch initiation
     * 5. Android Integration: Interfaces with accessibility service dispatch
     * 6. Callback Management: Handles completion/cancellation with error isolation
     * 7. Error Recovery: Graceful degradation with comprehensive logging
     *
     * Integration Benefits:
     * - Single point of truth for all gesture execution
     * - Consistent error handling across all gesture types
     * - Automatic pattern learning without manual intervention
     * - Seamless state coordination across all system components
     * - Isolated callback execution prevents cascade failures
     *
     * Critical Implementation Details:
     * - GesturePatternRecorder integration enables automatic pattern learning
     * - GestureLockManager integration enables gesture repeat features
     * - Exception isolation in callbacks prevents system-wide failures
     * - State manager integration provides system-wide coordination
     *
     * @param gestureDescription Android gesture description specifying the physical gesture
     * @param gestureType Switchify gesture type for system coordination and logging
     * @param gestureData Optional data package for pattern recording and gesture locking
     * @param resultHandler Optional custom callback handler, defaults to state manager integration
     */
    fun dispatch(
        gestureDescription: GestureDescription,
        gestureType: GestureType,
        gestureData: GestureData?,
        resultHandler: GestureResultHandler? = null
    ) {
        val handler = resultHandler ?: DefaultResultHandler(gestureType)
        val baseData = mapOf(
            "gesture_type" to gestureType.name.lowercase(),
            "finger_count" to gestureData?.fingerCount,
            "has_gesture_data" to (gestureData != null)
        )

        try {
            // Handle gesture pattern recording and gesture lock
            gestureData?.let { data ->
                GestureCaptureRouter.onGesturePerformed(data)
                GesturePatternRecorder.addGesture(data, accessibilityService)
            }

            // Notify state manager of dispatch attempt
            GestureStateManager.notifyGestureDispatchStarted(gestureType)

            accessibilityService.dispatchGesture(
                gestureDescription,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        try {
                            handler.onGestureCompleted(gestureType)
                        } catch (e: Exception) {
                            Logger.log(
                                LogEvent.GestureDispatchFailed,
                                data = baseData + mapOf(
                                    "result" to "failure",
                                    "reason" to "completion_handler_exception"
                                ),
                                throwable = e
                            )
                            android.util.Log.e(
                                "GestureDispatcher",
                                "Error in completion handler",
                                e
                            )
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        try {
                            handler.onGestureCancelled(gestureType)
                        } catch (e: Exception) {
                            Logger.log(
                                LogEvent.GestureDispatchFailed,
                                data = baseData + mapOf(
                                    "result" to "failure",
                                    "reason" to "cancellation_handler_exception"
                                ),
                                throwable = e
                            )
                            android.util.Log.e(
                                "GestureDispatcher",
                                "Error in cancellation handler",
                                e
                            )
                        }
                    }
                },
                null
            )
        } catch (e: Exception) {
            Logger.log(
                LogEvent.GestureDispatchFailed,
                data = baseData + mapOf(
                    "result" to "failure",
                    "reason" to "dispatch_exception"
                ),
                throwable = e
            )
            handler.onGestureError(gestureType, e)
        }
    }

    /**
     * Convenience method for dispatching simple gestures with automatic result handling.
     *
     * @param gestureDescription The gesture description to dispatch
     * @param gestureType The type of gesture being dispatched
     * @param gestureData Optional gesture data for pattern recording and gesture lock
     */
    fun dispatchSimple(
        gestureDescription: GestureDescription,
        gestureType: GestureType,
        gestureData: GestureData? = null
    ) {
        dispatch(gestureDescription, gestureType, gestureData)
    }

    /**
     * Dispatches a gesture with custom completion and cancellation actions.
     *
     * @param gestureDescription The gesture description to dispatch
     * @param gestureType The type of gesture being dispatched
     * @param gestureData Optional gesture data for pattern recording and gesture lock
     * @param onCompleted Action to execute on successful completion
     * @param onCancelled Action to execute on cancellation
     * @param onError Action to execute on error
     */
    fun dispatchWithActions(
        gestureDescription: GestureDescription,
        gestureType: GestureType,
        gestureData: GestureData? = null,
        onCompleted: (() -> Unit)? = null,
        onCancelled: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val handler = object : GestureResultHandler {
            override fun onGestureCompleted(gestureType: GestureType) {
                GestureStateManager.notifyGestureDispatchCompleted(gestureType)
                onCompleted?.invoke()
            }

            override fun onGestureCancelled(gestureType: GestureType) {
                GestureStateManager.notifyGestureDispatchCancelled(gestureType)
                onCancelled?.invoke()
            }

            override fun onGestureError(gestureType: GestureType, error: Throwable) {
                android.util.Log.e(
                    "GestureDispatcher",
                    "Gesture dispatch error for $gestureType",
                    error
                )
                GestureStateManager.notifyGestureDispatchError(gestureType, error)
                onError?.invoke(error)
            }
        }

        dispatch(gestureDescription, gestureType, gestureData, handler)
    }

    /**
     * Checks if the accessibility service is available for gesture dispatch.
     *
     * @return True if service is available, false otherwise
     */
    fun isServiceAvailable(): Boolean {
        return try {
            accessibilityService.serviceInfo != null
        } catch (e: Exception) {
            false
        }
    }
}
