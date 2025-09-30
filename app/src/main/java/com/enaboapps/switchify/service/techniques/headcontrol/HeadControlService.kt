package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.utils.DeviceLockObserver
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
    private var deviceLockObserver: DeviceLockObserver? = null
    private var enableAfterUnlock = false
    private val cameraPermissionManager = CameraPermissionManager.getInstance(context)
    
    /**
     * Initialize head control service
     */
    fun initialize() {
        val enabled = settings.isHeadControlEnabled()
        Log.d(TAG, "initialize() called, head control enabled: $enabled")
        
        // Setup device lock observer
        setupDeviceLockObserver()
        
        // Setup camera permission monitoring
        setupCameraPermissionMonitoring()
        
        if (enabled) {
            if (!cameraPermissionManager.hasPermission()) {
                Log.w(TAG, "Head control enabled in settings but camera permission missing; will enable when permission granted.")
                showCameraPermissionRequiredNotification()
                return
            }
            
            if (!DeviceLockObserver.isUserUnlocked(context)) {
                Log.w(TAG, "Head control enabled in settings but device is locked; will enable after unlock.")
                enableAfterUnlock = true
                showDeviceLockedNotification()
                return
            }
            
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
     * Check if head control is fully initialized and ready for use.
     * @return true if head control is enabled AND fully initialized
     */
    fun isReady(): Boolean = isEnabled() && headControlManager?.isReady() == true
    
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
     * Show camera permission required notification
     */
    private fun showCameraPermissionRequiredNotification() {
        ServiceMessageHUD.instance.showMessage(
            R.string.hud_head_control_requires_camera_permission,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )
    }
    
    /**
     * Enable or disable head control with permission and device state validation
     * @param enabled Whether to enable head control
     * @return true if operation succeeded, false if blocked due to missing permission or locked device
     */
    fun setEnabled(enabled: Boolean): Boolean {
        Log.d(TAG, "setEnabled called with: $enabled, current manager exists: ${headControlManager != null}")
        
        if (enabled) {
            // Check camera permission first
            if (!CameraPermissionManager.getInstance(context).hasPermission()) {
                Log.w(TAG, "Cannot enable head control - camera permission not granted")
                showCameraPermissionRequiredNotification()
                return false
            }
            
            // Check device unlock status
            if (!DeviceLockObserver.isUserUnlocked(context)) {
                Log.w(TAG, "Cannot enable head control - device is locked")
                enableAfterUnlock = true
                showDeviceLockedNotification()
                return false
            }
        }
        
        // Clear pending unlock enablement if disabling
        if (!enabled) {
            enableAfterUnlock = false
        }
        
        return try {
            if (enabled && headControlManager == null) {
                Log.d(TAG, "Initializing head control manager")
                // Create HeadControlManager on main thread since it contains UI components
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                var success = false
                val latch = java.util.concurrent.CountDownLatch(1)

                mainHandler.post {
                    try {
                        headControlManager = HeadControlManager(context)
                        success = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create HeadControlManager on main thread", e)
                        success = false
                    } finally {
                        latch.countDown()
                    }
                }

                // Wait for main thread creation to complete
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)

                if (success) {
                    // Only update settings after successful initialization
                    settings.setHeadControlEnabled(enabled)
                } else {
                    return false
                }
            } else if (!enabled) {
                Log.d(TAG, "Disabling head control manager")
                // Cleanup on main thread since it involves UI components
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                val latch = java.util.concurrent.CountDownLatch(1)

                mainHandler.post {
                    try {
                        headControlManager?.cleanup()
                        headControlManager = null
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to cleanup HeadControlManager on main thread", e)
                    } finally {
                        latch.countDown()
                    }
                }

                // Wait for main thread cleanup to complete
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                settings.setHeadControlEnabled(enabled)
            } else {
                // Already in desired state, just update settings
                settings.setHeadControlEnabled(enabled)
            }
            true
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Security exception when setting head control enabled=$enabled - disabling", securityException)
            // Only disable for security/permission issues
            settings.setHeadControlEnabled(false)
            headControlManager?.cleanup()
            headControlManager = null
            false
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to setEnabled($enabled) - leaving setting unchanged", t)
            // For other exceptions, don't change the setting but clean up manager if needed
            if (enabled && headControlManager == null) {
                // Failed to create manager, but don't disable the setting
                Log.w(TAG, "Head control remains enabled in settings but manager failed to initialize")
            }
            false
        }
    }
    
    /**
     * Setup device lock observer to monitor unlock events
     */
    private fun setupDeviceLockObserver() {
        deviceLockObserver?.stopObserving()
        deviceLockObserver = DeviceLockObserver(context)
        deviceLockObserver?.startObserving(
            onUnlocked = {
                Log.d(TAG, "Device unlocked")
                if (enableAfterUnlock) {
                    Log.i(TAG, "Enabling head control after device unlock")
                    enableAfterUnlock = false
                    if (CameraPermissionManager.getInstance(context).hasPermission()) {
                        headControlManager = HeadControlManager(context)
                        ServiceMessageHUD.instance.showMessage(
                            R.string.hud_head_control_enabled_after_unlock,
                            ServiceMessageHUD.MessageType.DISAPPEARING
                        )
                    } else {
                        Log.w(TAG, "Cannot enable head control after unlock - camera permission missing")
                        showCameraPermissionRequiredNotification()
                    }
                }
            },
            onLocked = {
                Log.d(TAG, "Device locked")
                // Optionally disable head control when device locks
                // For now, we'll let it continue running if already active
            }
        )
    }
    
    /**
     * Setup camera permission monitoring to handle runtime permission changes
     */
    private fun setupCameraPermissionMonitoring() {
        cameraPermissionManager.startMonitoring(
            onGranted = {
                Log.d(TAG, "Camera permission granted")
                // Check if we should enable head control now that permission is available
                if (settings.isHeadControlEnabled() && headControlManager == null) {
                    if (DeviceLockObserver.isUserUnlocked(context)) {
                        Log.i(TAG, "Enabling head control after camera permission granted")
                        headControlManager = HeadControlManager(context)
                        ServiceMessageHUD.instance.showMessage(
                            R.string.hud_head_control_enabled_after_permission_granted,
                            ServiceMessageHUD.MessageType.DISAPPEARING
                        )
                    } else {
                        Log.d(TAG, "Camera permission granted but device is locked; will enable after unlock")
                        enableAfterUnlock = true
                    }
                }
            },
            onRevoked = {
                Log.w(TAG, "Camera permission revoked - disabling head control")
                // Gracefully disable head control when permission is revoked
                headControlManager?.cleanup()
                headControlManager = null
                // Don't change settings - user didn't intentionally disable
                ServiceMessageHUD.instance.showMessage(
                    R.string.hud_head_control_disabled_permission_revoked,
                    ServiceMessageHUD.MessageType.PERMANENT
                )
            }
        )
    }
    
    /**
     * Show device locked notification
     */
    private fun showDeviceLockedNotification() {
        ServiceMessageHUD.instance.showMessage(
            R.string.hud_head_control_requires_unlocked_device,
            ServiceMessageHUD.MessageType.PERMANENT
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        cameraPermissionManager.stopMonitoring()
        deviceLockObserver?.stopObserving()
        deviceLockObserver = null
        headControlManager?.cleanup()
        headControlManager = null
        enableAfterUnlock = false
        Log.d(TAG, "Head control service cleaned up")
    }
}