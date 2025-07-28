package com.enaboapps.switchify.service.gestures.execution

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GestureStateManager
import com.enaboapps.switchify.service.gestures.data.GestureType

/**
 * Unified gesture dispatcher that consolidates all gesture dispatch operations.
 * Provides standardized dispatch handling, error management, and result callbacks.
 * Integrates with GestureStateManager for coordinated state management.
 */
class GestureDispatcher(
    private val accessibilityService: SwitchifyAccessibilityService
) {
    
    /**
     * Interface for handling gesture dispatch results.
     */
    interface GestureResultHandler {
        fun onGestureCompleted(gestureType: GestureType)
        fun onGestureCancelled(gestureType: GestureType)
        fun onGestureError(gestureType: GestureType, error: Throwable)
    }
    
    /**
     * Default result handler that integrates with GestureStateManager.
     */
    private class DefaultResultHandler(private val gestureType: GestureType) : GestureResultHandler {
        
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
            android.util.Log.e("GestureDispatcher", "Gesture dispatch error for $gestureType", error)
            GestureStateManager.notifyGestureDispatchError(gestureType, error)
        }
    }
    
    /**
     * Dispatches a gesture with unified error handling and state management.
     * 
     * @param gestureDescription The gesture description to dispatch
     * @param gestureType The type of gesture being dispatched
     * @param resultHandler Optional custom result handler, uses default if null
     */
    fun dispatch(
        gestureDescription: GestureDescription,
        gestureType: GestureType,
        resultHandler: GestureResultHandler? = null
    ) {
        val handler = resultHandler ?: DefaultResultHandler(gestureType)
        
        try {
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
                            android.util.Log.e("GestureDispatcher", "Error in completion handler", e)
                        }
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        try {
                            handler.onGestureCancelled(gestureType)
                        } catch (e: Exception) {
                            android.util.Log.e("GestureDispatcher", "Error in cancellation handler", e)
                        }
                    }
                },
                null
            )
        } catch (e: Exception) {
            handler.onGestureError(gestureType, e)
        }
    }
    
    /**
     * Convenience method for dispatching simple gestures with automatic result handling.
     * 
     * @param gestureDescription The gesture description to dispatch
     * @param gestureType The type of gesture being dispatched
     */
    fun dispatchSimple(gestureDescription: GestureDescription, gestureType: GestureType) {
        dispatch(gestureDescription, gestureType, null)
    }
    
    /**
     * Dispatches a gesture with custom completion and cancellation actions.
     * 
     * @param gestureDescription The gesture description to dispatch
     * @param gestureType The type of gesture being dispatched
     * @param onCompleted Action to execute on successful completion
     * @param onCancelled Action to execute on cancellation
     * @param onError Action to execute on error
     */
    fun dispatchWithActions(
        gestureDescription: GestureDescription,
        gestureType: GestureType,
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
                android.util.Log.e("GestureDispatcher", "Gesture dispatch error for $gestureType", error)
                GestureStateManager.notifyGestureDispatchError(gestureType, error)
                onError?.invoke(error)
            }
        }
        
        dispatch(gestureDescription, gestureType, handler)
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