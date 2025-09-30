package com.enaboapps.switchify.service.camera

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.enaboapps.switchify.service.core.CameraServiceController
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages camera lifecycle based on switch configuration and access technique.
 * Centralizes the decision-making for when camera should be active.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val deviceLockObserver: DeviceLockObserver,
    private val serviceScope: CoroutineScope,
    private val onServiceConnected: () -> Unit = {}
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    private var cameraController: CameraServiceController? = null
    private var cameraSwitchManager: CameraSwitchManager? = null

    init {
        setupCameraController()
    }

    private fun setupCameraController() {
        cameraController = CameraServiceController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            deviceLockObserver = deviceLockObserver,
            onServiceConnected = { onServiceConnected() },
            onServiceDisconnected = { /* No-op */ }
        )
    }

    /**
     * Evaluates if camera should be active and starts/stops accordingly.
     * Call this when switches are loaded/updated or access technique changes.
     */
    fun evaluateAndUpdateCameraState() {
        val currentTechnique = AccessTechnique.getCurrentTechnique()
        val switchEventProvider = ServiceCore.getSwitchEventProvider()
        val headControlService = ServiceCore.getHeadControlService()
        
        // Check if we need camera for switches
        val hasCameraSwitch = switchEventProvider?.hasCameraSwitch == true
        
        // Check head control status
        val headControlEnabled = headControlService?.isEnabled() == true
        val headControlReady = headControlService?.isReady() == true
        
        val shouldHaveCamera = shouldCameraBeActive(hasCameraSwitch)

        Log.d(TAG, "evaluateAndUpdateCameraState - technique: $currentTechnique, shouldHaveCamera: $shouldHaveCamera, hasSwitch: $hasCameraSwitch, headEnabled: $headControlEnabled, headReady: $headControlReady")

        if (shouldHaveCamera && !isCameraActive()) {
            startCamera()
        } else if (!shouldHaveCamera && isCameraActive()) {
            stopCamera()
        } else if (shouldHaveCamera && isCameraActive()) {
            // Camera should be active and is active - ensure service is bound
            bindCameraService()
        } else if (headControlEnabled && !headControlReady && !hasCameraSwitch) {
            // HeadControl is enabled but not ready yet - schedule retry
            Log.d(TAG, "HeadControl is enabled but not ready yet - camera startup will be triggered when ready")
        }
    }

    /**
     * Determines if camera should be active based on current conditions.
     */
    private fun shouldCameraBeActive(hasCameraSwitch: Boolean): Boolean {
        if (hasCameraSwitch) return true
        
        // Head control is now independent - check if it's enabled AND fully ready
        val headControlService = ServiceCore.getHeadControlService()
        if (headControlService?.isReady() == true) {
            Log.d(TAG, "Head control is ready - camera should be active")
            return true
        } else if (headControlService?.isEnabled() == true) {
            Log.d(TAG, "Head control is enabled but not ready yet - camera should wait")
            return false
        }
        
        return false
    }

    /**
     * Starts camera components when needed.
     */
    private fun startCamera() {
        if (!deviceLockObserver.isUserUnlocked()) {
            Log.d(TAG, "Device locked, cannot start camera")
            return
        }

        Log.d(TAG, "Starting camera components")
        initializeCameraSwitchManager()
        bindCameraService()
    }

    /**
     * Stops camera components when no longer needed.
     */
    private fun stopCamera() {
        Log.d(TAG, "Stopping camera components")
        cleanupCameraSwitchManager()
        unbindCameraService()
    }

    /**
     * Initializes the camera switch manager for gesture processing.
     */
    private fun initializeCameraSwitchManager() {
        val scanningManager = ServiceCore.getScanningManager() ?: return
        val switchEventProvider = ServiceCore.getSwitchEventProvider() ?: return

        if (cameraSwitchManager == null) {
            cameraSwitchManager = CameraSwitchManager(context, scanningManager, switchEventProvider)
            serviceScope.launch {
                cameraSwitchManager?.initialize()
            }
        }
    }

    /**
     * Cleans up camera switch manager.
     */
    private fun cleanupCameraSwitchManager() {
        cameraSwitchManager?.let {
            serviceScope.launch {
                it.cleanup()
                cameraSwitchManager = null
            }
        }
    }

    /**
     * Binds to camera foreground service.
     */
    private fun bindCameraService() {
        cameraController?.bindIfNeeded()
    }

    /**
     * Unbinds from camera foreground service.
     */
    private fun unbindCameraService() {
        cameraController?.unbindIfBound()
    }

    /**
     * Checks if camera is currently active.
     */
    private fun isCameraActive(): Boolean {
        return cameraSwitchManager != null
    }

    /**
     * Gets the camera switch manager instance.
     */
    fun getCameraSwitchManager(): CameraSwitchManager? = cameraSwitchManager

    /**
     * Gets the camera service controller instance.
     */
    fun getCameraController(): CameraServiceController? = cameraController
    
    /**
     * Called when HeadControl initialization is complete.
     * This triggers a re-evaluation of camera state to start camera if needed.
     */
    fun onHeadControlReady() {
        Log.d(TAG, "HeadControl is now ready - re-evaluating camera state")
        evaluateAndUpdateCameraState()
    }

    /**
     * Cleanup method to be called when service is destroyed.
     */
    fun cleanup() {
        cleanupCameraSwitchManager()
        unbindCameraService()
    }
}
