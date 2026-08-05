package com.enaboapps.switchify.service.camera

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.enaboapps.switchify.service.core.CameraServiceController
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
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
        val hasCameraSwitch = switchEventProvider?.hasCameraSwitch == true

        Log.d(
            TAG,
            "evaluateAndUpdateCameraState - technique: $currentTechnique, hasSwitch: $hasCameraSwitch"
        )

        Logger.log(
            LogEvent.CameraStateEvaluated,
            data = mapOf(
                "result" to "evaluated",
                "technique" to currentTechnique,
                "should_have_camera" to hasCameraSwitch,
                "has_camera_switch" to hasCameraSwitch,
                "camera_active" to isCameraActive()
            )
        )

        if (hasCameraSwitch && !isCameraActive()) {
            Logger.log(
                LogEvent.CameraStartAttempt,
                data = mapOf(
                    "result" to "started",
                    "reason" to "camera_required"
                )
            )
            startCamera()
        } else if (!hasCameraSwitch && isCameraActive()) {
            stopCamera()
        } else if (hasCameraSwitch && isCameraActive()) {
            bindCameraService()
        }
    }

    /**
     * Starts camera components when needed.
     */
    private fun startCamera() {
        if (!deviceLockObserver.isUserUnlocked()) {
            Log.d(TAG, "Device locked, cannot start camera")
            Logger.log(
                LogEvent.CameraStartFailed,
                data = mapOf(
                    "result" to "blocked",
                    "reason" to "device_locked"
                )
            )
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
        Logger.log(
            LogEvent.CameraStop,
            data = mapOf(
                "result" to "success",
                "reason" to "camera_not_required"
            )
        )
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
        val manager = cameraSwitchManager ?: return
        cameraSwitchManager = null
        serviceScope.launch {
            manager.cleanup()
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
     * Cleanup method to be called when service is destroyed.
     */
    fun cleanup() {
        cleanupCameraSwitchManager()
        unbindCameraService()
    }
}
