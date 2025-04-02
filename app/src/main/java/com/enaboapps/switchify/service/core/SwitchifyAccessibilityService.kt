package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.actions.AudioActionManager
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.service.utils.KeyboardBridge
import com.enaboapps.switchify.service.utils.ScreenWatcher
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.utils.Logger
import com.enaboapps.switchify.utils.Resources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * This is the main service class for the Switchify application.
 * It extends the AccessibilityService class to provide accessibility features.
 */
class SwitchifyAccessibilityService : AccessibilityService(), LifecycleOwner,
    SwitchEventProvider.CameraSwitchListener {

    private var cameraSwitchManager: CameraSwitchManager? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var screenWatcher: ScreenWatcher
    private lateinit var scanSettings: ScanSettings
    private lateinit var deviceLockObserver: DeviceLockObserver
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "SwitchifyAccessibilityService"
    }

    override fun onCreate() {
        super.onCreate()

        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        deviceLockObserver = DeviceLockObserver(this)
    }

    private fun setup() {
        Logger.init(this)

        Resources.init(this)

        IAPHandler.initialize(context = this, connectToRevenueCat = false)

        AccessTechnique.init(this.applicationContext)

        GlobalActionManager.init(this)
        AudioActionManager.init(this)

        ServiceCore.init(this)

        val scanningManager = ServiceCore.getScanningManager() ?: return
        val externalSwitchListener = ServiceCore.getExternalSwitchListener() ?: return

        screenWatcher = ScreenWatcher(
            onScreenSleep = {
                externalSwitchListener.reset()
                scanningManager.reset()
            },
            onOrientationChanged = { scanningManager.reset() }
        )
        screenWatcher.register(this)

        scanSettings = ScanSettings(this)

        GestureManager.Companion.getInstance().setup(this)
        SelectionHandler.init(this)

        deviceLockObserver.startObserving(
            onUnlocked = {
                initProtectedServiceComponents()
            },
            onLocked = {
                Log.d(TAG, "Device locked")
            })

        // Initialize components that require device unlock
        initProtectedServiceComponents()
    }

    /**
     * Initializes protected service components if the device is unlocked.
     */
    private fun initProtectedServiceComponents() {
        if (deviceLockObserver.isUserUnlocked()) {
            Log.d(TAG, "Device unlocked, initializing protected components")
            // Initialize components that require device unlock
            IAPHandler.connect(context = this)
            initCameraSwitchManager()
        }
    }

    /**
     * Initializes and starts the camera switch manager if the device is unlocked and a camera switch is available.
     */
    private fun initCameraSwitchManager() {
        val scanningManager = ServiceCore.getScanningManager() ?: return
        val switchEventProvider = ServiceCore.getSwitchEventProvider() ?: return
        if (deviceLockObserver.isUserUnlocked() && switchEventProvider.hasCameraSwitch && cameraSwitchManager == null) {
            cameraSwitchManager = CameraSwitchManager(this, scanningManager, switchEventProvider)
            serviceScope.launch {
                delay(1000)
                cameraSwitchManager?.initialize()
                delay(3000)
                cameraSwitchManager?.startCamera(this@SwitchifyAccessibilityService)
            }
        }
    }

    /**
     * Stops the camera switch manager if it is running.
     */
    private fun stopCameraSwitchManager() {
        if (cameraSwitchManager != null) {
            serviceScope.launch {
                cameraSwitchManager?.stopCamera()
                cameraSwitchManager = null
            }
        }
    }

    /**
     * This method is called when an AccessibilityEvent is fired.
     * It updates the nodes in the active window and the keyboard state.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        serviceScope.launch {
            NodeExaminer.examineAccessibilityTree(
                rootInActiveWindow,
                windows,
                this@SwitchifyAccessibilityService,
                this
            )
            KeyboardBridge.updateKeyboardState(windows, scanSettings)
        }
    }

    override fun onInterrupt() {
        SwitchifyAccessibilityWindow.instance.cleanup()
        Logger.logEvent("Service Interrupted")
    }

    /**
     * This method is called when the service is connected.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()

        setup()

        Logger.logEvent("Service Connected")

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        serviceScope.launch {
            NodeExaminer.examineAccessibilityTree(
                rootInActiveWindow,
                windows,
                this@SwitchifyAccessibilityService,
                this
            )
            KeyboardBridge.updateKeyboardState(windows, scanSettings)
        }

        // Update the SystemNodeScanner with the current layout info
        serviceScope.launch {
            val scanningManager = ServiceCore.getScanningManager() ?: return@launch
            NodeExaminer.getActionableNodesFlow().collect { nodes ->
                scanningManager.updateActionableNodes(nodes)
            }
        }

        // Update the KeyboardScanner with the current layout info
        serviceScope.launch {
            val scanningManager = ServiceCore.getScanningManager() ?: return@launch
            NodeExaminer.getKeyboardNodesFlow().collect { nodes ->
                scanningManager.updateKeyboardNodes(nodes)
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        cameraSwitchManager?.stopCamera()
        deviceLockObserver.stopObserving()
        ServiceCore.cleanup()
        GlobalActionManager.cleanup()
        AudioActionManager.cleanup()

        Logger.logEvent("Service Unbound")

        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        SwitchifyAccessibilityWindow.instance.onServiceDestroy()
        Logger.logEvent("Service Destroyed")
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null) {
            return handleSwitchEvent(event)
        }
        return false
    }

    /**
     * This method handles switch press events.
     * It performs different actions based on the type of the key event.
     */
    private fun handleSwitchEvent(event: KeyEvent): Boolean {
        val externalSwitchListener = ServiceCore.getExternalSwitchListener() ?: return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> externalSwitchListener.onSwitchPressed(event.keyCode)
            KeyEvent.ACTION_UP -> externalSwitchListener.onSwitchReleased(event.keyCode)
            else -> false
        }
    }

    override fun onCameraSwitchAvailabilityChanged(available: Boolean) {
        if (available) {
            initCameraSwitchManager()
        } else {
            stopCameraSwitchManager()
        }

        Log.d(TAG, "Camera switch availability changed: $available")
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
}