package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.window.ServiceMessageHUD

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
        val enabled = settings.isHeadControlEnabled()
        Log.d(TAG, "initialize() called, head control enabled: $enabled")
        if (enabled) {
            Log.d(TAG, "Creating HeadControlManager")
            headControlManager = HeadControlManager(context)
        } else {
            Log.d(TAG, "Head control disabled, skipping manager creation")
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
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "updateHeadPosition called: ($headRotationX, $headRotationY), manager exists: ${headControlManager != null}")
        }
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
        Log.d(TAG, "setMenuMode called with: $enabled, manager exists: ${headControlManager != null}")
        headControlManager?.setMenuMode(enabled)
    }
    
    /**
     * Refresh menu nodes when menu changes
     */
    fun refreshMenuNodes() {
        headControlManager?.refreshMenuNodes()
    }
    
    /**
     * Show camera permission required notification
     */
    private fun showCameraPermissionRequiredNotification() {
        ServiceMessageHUD.instance.showMessage(
            R.string.head_control_requires_camera_permission,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )
    }
    
    /**
     * Enable or disable head control with permission validation
     * @param enabled Whether to enable head control
     * @return true if operation succeeded, false if blocked due to missing permission
     */
    fun setEnabled(enabled: Boolean): Boolean {
        Log.d(TAG, "setEnabled called with: $enabled, current manager exists: ${headControlManager != null}")
        
        // Check camera permission when enabling
        if (enabled && !CameraPermissionManager.getInstance(context).hasPermission()) {
            Log.w(TAG, "Cannot enable head control - camera permission not granted")
            showCameraPermissionRequiredNotification()
            return false
        }
        
        settings.setHeadControlEnabled(enabled)
        if (enabled && headControlManager == null) {
            Log.d(TAG, "Initializing head control manager")
            initialize()
        } else if (!enabled) {
            Log.d(TAG, "Disabling head control manager")
            headControlManager?.cleanup()
            headControlManager = null
        }
        
        return true
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