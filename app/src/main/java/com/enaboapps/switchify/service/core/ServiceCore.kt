package com.enaboapps.switchify.service.core

import com.enaboapps.switchify.service.pauseresume.PauseManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.switches.external.ExternalSwitchListener
import java.lang.ref.WeakReference

object ServiceCore {
    private lateinit var scanningManagerRef: WeakReference<ScanningManager>
    private lateinit var externalSwitchListenerRef: WeakReference<ExternalSwitchListener>
    private lateinit var switchEventProviderRef: WeakReference<SwitchEventProvider>

    /**
     * Initializes the service core with the given context and accessibility service.
     * @param accessibilityService The accessibility service instance.
     */
    fun init(accessibilityService: SwitchifyAccessibilityService) {
        // Initialize PauseManager singleton
        PauseManager.getInstance().init(accessibilityService)

        scanningManagerRef = WeakReference(ScanningManager(accessibilityService))
        switchEventProviderRef = WeakReference(SwitchEventProvider(accessibilityService))
        val scanningManager = scanningManagerRef.get() ?: return
        val switchEventProvider = switchEventProviderRef.get() ?: return
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
        return scanningManagerRef.get()
    }

    /**
     * Gets the external switch listener instance.
     * @return The external switch listener instance or null if not initialized.
     */
    fun getExternalSwitchListener(): ExternalSwitchListener? {
        return externalSwitchListenerRef.get()
    }

    /**
     * Gets the switch event provider instance.
     * @return The switch event provider instance or null if not initialized.
     */
    fun getSwitchEventProvider(): SwitchEventProvider? {
        return switchEventProviderRef.get()
    }

    /**
     * Gets the pause manager instance.
     * @return The pause manager instance (singleton)
     */
    fun getPauseManager(): PauseManager {
        return PauseManager.getInstance()
    }

    /**
     * Cleans up the service core.
     */
    fun cleanup() {
        scanningManagerRef.get()?.shutdown()
        scanningManagerRef = WeakReference(null)
        switchEventProviderRef = WeakReference(null)
        externalSwitchListenerRef = WeakReference(null)
    }
}