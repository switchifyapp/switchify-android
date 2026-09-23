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
import java.lang.ref.WeakReference

object ServiceCore {
    private lateinit var scanningManagerRef: WeakReference<ScanningManager>
    private lateinit var externalSwitchListenerRef: WeakReference<ExternalSwitchListener>
    private lateinit var switchEventProviderRef: WeakReference<SwitchEventProvider>
    private lateinit var cameraManagerRef: WeakReference<CameraManager>
    @SuppressLint("StaticFieldLeak")
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
        scanningManagerRef = WeakReference(
            ScanningManager(accessibilityService, requireNotNull(gestureTargetIndicator))
        )
        switchEventProviderRef = WeakReference(SwitchEventProvider(accessibilityService))

        val scanningManager = scanningManagerRef.get() ?: return
        val switchEventProvider = switchEventProviderRef.get() ?: return
        switchProfileActivationCoordinator = SwitchProfileActivationCoordinator(
            accessibilityService,
            switchEventProvider,
            accessibilityService.getServiceScope()
        )
        SwitchifyRemoteBridgeCoordinator.attach(switchEventProvider)

        scanningManager.setup()
        externalSwitchListenerRef =
            WeakReference(
                ExternalSwitchListener(
                    accessibilityService,
                    scanningManager,
                    switchEventProvider
                )
            )
    }

    /**
     * Gets the scanning manager instance.
     * @return The scanning manager instance or null if not initialized.
     */
    fun getScanningManager(): ScanningManager? {
        return if (::scanningManagerRef.isInitialized) scanningManagerRef.get() else null
    }

    fun getGestureTargetIndicator(): GestureTargetIndicatorController? = gestureTargetIndicator

    /**
     * Gets the external switch listener instance.
     * @return The external switch listener instance or null if not initialized.
     */
    fun getExternalSwitchListener(): ExternalSwitchListener? {
        return if (::externalSwitchListenerRef.isInitialized) externalSwitchListenerRef.get() else null
    }

    /**
     * Gets the switch event provider instance.
     * @return The switch event provider instance or null if not initialized.
     */
    fun getSwitchEventProvider(): SwitchEventProvider? {
        return if (::switchEventProviderRef.isInitialized) switchEventProviderRef.get() else null
    }

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
        cameraManagerRef = WeakReference(cameraManager)
    }

    /**
     * Gets the camera manager instance.
     * @return The camera manager instance or null if not initialized.
     */
    fun getCameraManager(): CameraManager? {
        return if (::cameraManagerRef.isInitialized) cameraManagerRef.get() else null
    }

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
        if (::scanningManagerRef.isInitialized) {
            scanningManagerRef.get()?.shutdown()
            scanningManagerRef = WeakReference(null)
        }
        if (::switchEventProviderRef.isInitialized) {
            switchEventProviderRef = WeakReference(null)
        }
        if (::externalSwitchListenerRef.isInitialized) {
            externalSwitchListenerRef.get()?.shutdown()
            externalSwitchListenerRef = WeakReference(null)
        }
        if (::cameraManagerRef.isInitialized) {
            cameraManagerRef = WeakReference(null)
        }
        switchProfileActivationCoordinator = null
    }
}
