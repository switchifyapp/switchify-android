package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.service.camera.CameraManager
import com.enaboapps.switchify.service.gestures.visuals.AndroidGestureTargetIndicatorRenderer
import com.enaboapps.switchify.service.gestures.visuals.GestureTargetIndicatorController
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.service.pauseresume.PauseManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.switches.external.ExternalSwitchListener
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlService
import java.lang.ref.WeakReference

object ServiceCore {
    private lateinit var scanningManagerRef: WeakReference<ScanningManager>
    private lateinit var externalSwitchListenerRef: WeakReference<ExternalSwitchListener>
    private lateinit var switchEventProviderRef: WeakReference<SwitchEventProvider>
    private lateinit var headControlServiceRef: WeakReference<HeadControlService>
    private lateinit var cameraManagerRef: WeakReference<CameraManager>
    private var pcServiceConnectionController: PcServiceConnectionController? = null
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
        headControlServiceRef = WeakReference(HeadControlService.getInstance(accessibilityService))

        val scanningManager = scanningManagerRef.get() ?: return
        val switchEventProvider = switchEventProviderRef.get() ?: return
        val headControlService = headControlServiceRef.get() ?: return

        scanningManager.setup()
        headControlService.initialize()
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

    /**
     * Gets the pause manager instance.
     * @return The pause manager instance (singleton)
     */
    fun getPauseManager(): PauseManager {
        return PauseManager.getInstance()
    }

    /**
     * Gets the head control service instance.
     * @return The head control service instance or null if not initialized.
     */
    fun getHeadControlService(): HeadControlService? {
        return if (::headControlServiceRef.isInitialized) headControlServiceRef.get() else null
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

    fun setPcServiceConnectionController(controller: PcServiceConnectionController) {
        pcServiceConnectionController = controller
    }

    fun getPcServiceConnectionController(): PcServiceConnectionController? {
        return pcServiceConnectionController
    }

    /**
     * Cleans up the service core.
     */
    fun cleanup() {
        gestureTargetIndicator?.release()
        gestureTargetIndicator = null
        if (::scanningManagerRef.isInitialized) {
            scanningManagerRef.get()?.shutdown()
            scanningManagerRef = WeakReference(null)
        }
        if (::headControlServiceRef.isInitialized) {
            headControlServiceRef.get()?.cleanup()
            headControlServiceRef = WeakReference(null)
        }
        if (::switchEventProviderRef.isInitialized) {
            switchEventProviderRef = WeakReference(null)
        }
        if (::externalSwitchListenerRef.isInitialized) {
            externalSwitchListenerRef = WeakReference(null)
        }
        if (::cameraManagerRef.isInitialized) {
            cameraManagerRef = WeakReference(null)
        }
        // The PC connection controller is an app-scoped singleton shared with the
        // app UI; it manages its own session lifecycle and outlives the service.
    }
}
