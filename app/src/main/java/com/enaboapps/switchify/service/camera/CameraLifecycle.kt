package com.enaboapps.switchify.service.camera

import kotlinx.coroutines.flow.StateFlow

/**
 * Standardized lifecycle interface for all camera-related components.
 * Provides consistent initialization, cleanup, and state management patterns.
 */
interface CameraLifecycle {
    
    /**
     * Current lifecycle state of the component.
     */
    enum class State {
        UNINITIALIZED,
        INITIALIZING, 
        READY,
        ERROR,
        CLEANING_UP,
        DESTROYED
    }
    
    /**
     * Observable state flow for the current lifecycle state.
     */
    val lifecycleState: StateFlow<State>
    
    /**
     * Initialize the component asynchronously.
     * Should be idempotent - safe to call multiple times.
     * 
     * @return true if initialization started successfully, false if already initialized or failed
     */
    suspend fun initialize(): Boolean
    
    /**
     * Clean up all resources asynchronously.
     * Should be idempotent - safe to call multiple times.
     * Should handle partial initialization cleanup gracefully.
     * 
     * @return true if cleanup completed successfully
     */
    suspend fun cleanup(): Boolean
    
    /**
     * Check if the component is currently ready for use.
     */
    fun isReady(): Boolean = lifecycleState.value == State.READY
    
    /**
     * Check if the component has been destroyed.
     */
    fun isDestroyed(): Boolean = lifecycleState.value == State.DESTROYED
}