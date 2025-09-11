package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.menu.MenuManager

/**
 * Global service for head control functionality
 * Provides system-wide access to head control features
 */
class HeadControlService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "HeadControlService"
        
        @Volatile
        private var INSTANCE: HeadControlService? = null
        
        fun getInstance(context: Context): HeadControlService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HeadControlService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var headControlManager: HeadControlManager? = null
    private val settings = HeadControlSettings(context)
    
    /**
     * Initialize head control service
     */
    fun initialize() {
        if (settings.isHeadControlEnabled()) {
            Log.d(TAG, "Initializing head control")
            headControlManager = HeadControlManager(context)
        }
    }
    
    /**
     * Check if head control is enabled
     */
    fun isEnabled(): Boolean = settings.isHeadControlEnabled()
    
    /**
     * Get the head control manager instance
     */
    fun getHeadControlManager(): HeadControlManager? = headControlManager
    
    /**
     * Update head position from camera
     */
    fun updateHeadPosition(headRotationX: Float, headRotationY: Float) {
        headControlManager?.updateHeadPosition(headRotationX, headRotationY)
    }
    
    /**
     * Process gesture from camera
     */
    fun processGesture(gestureId: String, isStarting: Boolean) {
        headControlManager?.processGesture(gestureId, isStarting)
    }
    
    /**
     * Set menu mode for head control
     */
    fun setMenuMode(enabled: Boolean) {
        headControlManager?.setMenuMode(enabled)
    }
    
    /**
     * Refresh menu nodes when menu changes
     */
    fun refreshMenuNodes() {
        headControlManager?.refreshMenuNodes()
    }
    
    /**
     * Enable or disable head control
     */
    fun setEnabled(enabled: Boolean) {
        settings.setHeadControlEnabled(enabled)
        if (enabled && headControlManager == null) {
            initialize()
        } else if (!enabled) {
            headControlManager?.cleanup()
            headControlManager = null
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        headControlManager?.cleanup()
        headControlManager = null
        Log.d(TAG, "Head control service cleaned up")
    }
}