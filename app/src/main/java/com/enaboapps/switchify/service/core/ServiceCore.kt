package com.enaboapps.switchify.service.core

import android.annotation.SuppressLint
import com.enaboapps.switchify.service.camera.CameraManager
import com.enaboapps.switchify.service.gestures.PinchGesturePerformer
import com.enaboapps.switchify.service.gestures.visuals.AndroidGestureTargetIndicatorRenderer
import com.enaboapps.switchify.service.gestures.visuals.GestureTargetIndicatorController
import com.enaboapps.switchify.service.remotebridge.SwitchifyRemoteBridgeCoordinator
import com.enaboapps.switchify.service.pauseresume.PauseManager
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.switches.SwitchProfileActivationCoordinator
import com.enaboapps.switchify.service.switches.external.ExternalSwitchListener

@SuppressLint("StaticFieldLeak")
object ServiceCore {
    private var scanningManager: ScanningManager? = null
    private var externalSwitchListener: ExternalSwitchListener? = null
    private var switchEventProvider: SwitchEventProvider? = null
    private var cameraManager: CameraManager? = null
    private var switchProfileActivationCoordinator: SwitchProfileActivationCoordinator? = null
    private var gestureTargetIndicator: GestureTargetIndicatorController? = null

    /**
     * Initializes the service core with the given context and accessibility service.
     * @param accessibilityService The accessibility service instance.
     */
    fun init(accessibilityService: SwitchifyAccessibilityService) {
        // Initialize PauseManager singleton
        PauseManager.getInstance().init(accessibilityService)

        gestureTargetIndicator = GestureTargetIndicatorController(
            AndroidGestureTargetIndicatorRenderer(accessibilityService)
        )
        val scanningManager = ScanningManager(
            accessibilityService,
            requireNotNull(gestureTargetIndicator)
        )
        val switchEventProvider = SwitchEventProvider(accessibilityService)
        this.scanningManager = scanningManager
        this.switchEventProvider = switchEventProvider

        switchProfileActivationCoordinator = SwitchProfileActivationCoordinator(
            accessibilityService,
            switchEventProvider,
            accessibilityService.getServiceScope()
        )
        SwitchifyRemoteBridgeCoordinator.attach(switchEventProvider)

        scanningManager.setup()
        externalSwitchListener = ExternalSwitchListener(
            accessibilityService,
            scanningManager,
            switchEventProvider
        )
    }

    /**
     * Gets the scanning manager instance.
     * @return The scanning manager instance or null if not initialized.
     */
    fun getScanningManager(): ScanningManager? = scanningManager

    fun getGestureTargetIndicator(): GestureTargetIndicatorController? = gestureTargetIndicator

    /**
     * Gets the external switch listener instance.
     * @return The external switch listener instance or null if not initialized.
     */
    fun getExternalSwitchListener(): ExternalSwitchListener? = externalSwitchListener

    /**
     * Gets the switch event provider instance.
     * @return The switch event provider instance or null if not initialized.
     */
    fun getSwitchEventProvider(): SwitchEventProvider? = switchEventProvider

    internal fun getSwitchProfileActivationCoordinator(): SwitchProfileActivationCoordinator? {
        return switchProfileActivationCoordinator
    }

    /**
     * Gets the pause manager instance.
     * @return The pause manager instance (singleton)
     */
    fun getPauseManager(): PauseManager {
        return PauseManager.getInstance()
    }

    /**
     * Sets the camera manager instance.
     * @param cameraManager The camera manager instance to set.
     */
    fun setCameraManager(cameraManager: CameraManager) {
        this.cameraManager = cameraManager
    }

    /**
     * Gets the camera manager instance.
     * @return The camera manager instance or null if not initialized.
     */
    fun getCameraManager(): CameraManager? = cameraManager

    /**
     * Cleans up the service core.
     */
    fun cleanup() {
        MenuManager.getInstance().cleanup()
        getSwitchProfileActivationCoordinator()?.cancel(showMessage = false)
        SwitchifyRemoteBridgeCoordinator.detach()
        gestureTargetIndicator?.release()
        gestureTargetIndicator = null
        PinchGesturePerformer.cleanup()
        externalSwitchListener?.shutdown()
        externalSwitchListener = null
        scanningManager?.shutdown()
        scanningManager = null
        switchEventProvider?.shutdown()
        switchEventProvider = null
        cameraManager = null
        switchProfileActivationCoordinator = null
    }
}
