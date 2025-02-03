package com.enaboapps.switchify.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.lockscreen.LockScreenView
import com.enaboapps.switchify.service.methods.nodes.NodeExaminer
import com.enaboapps.switchify.service.scanning.ScanMethod
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.service.switches.external.ExternalSwitchListener
import com.enaboapps.switchify.service.utils.KeyboardBridge
import com.enaboapps.switchify.service.utils.ScreenWatcher
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * This is the main service class for the Switchify application.
 * It extends the AccessibilityService class to provide accessibility features.
 */
class SwitchifyAccessibilityService : AccessibilityService(), LifecycleOwner {

    private lateinit var scanningManager: ScanningManager
    private lateinit var switchEventProvider: SwitchEventProvider
    private lateinit var externalSwitchListener: ExternalSwitchListener
    private lateinit var cameraSwitchManager: CameraSwitchManager
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var screenWatcher: ScreenWatcher
    private lateinit var lockScreenView: LockScreenView
    private lateinit var scanSettings: ScanSettings
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        lifecycleRegistry = LifecycleRegistry(this)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    private fun setup() {
        Logger.init(this)

        ScanMethod.preferenceManager = PreferenceManager(this.applicationContext)

        scanningManager = ScanningManager(this, this)
        scanningManager.setup()

        cameraSwitchManager = CameraSwitchManager(this, scanningManager)

        lockScreenView = LockScreenView(this)
        lockScreenView.setup(this)

        screenWatcher = ScreenWatcher(
            onScreenWake = {
                lockScreenView.show()
                if (switchEventProvider.hasCameraSwitch) {
                    cameraSwitchManager.startCamera(this@SwitchifyAccessibilityService)
                }
                IAPHandler.refreshPurchaseStatus()
            },
            onScreenSleep = {
                externalSwitchListener.reset()
                scanningManager.reset()
                lockScreenView.hide()
                if (switchEventProvider.hasCameraSwitch) {
                    cameraSwitchManager.stopCamera()
                }
            },
            onOrientationChanged = { scanningManager.reset() }
        )
        screenWatcher.register(this)

        scanSettings = ScanSettings(this)

        switchEventProvider = SwitchEventProvider(this)
        externalSwitchListener = ExternalSwitchListener(this, scanningManager)

        GestureManager.getInstance().setup(this)
        SelectionHandler.init(this)

        IAPHandler.initialize(this)
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

    override fun onInterrupt() {}

    /**
     * This method is called when the service is connected.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()

        setup()

        Logger.logEvent("Service Connected")

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        if (switchEventProvider.hasCameraSwitch) {
            cameraSwitchManager.startCamera(this)
        }

        switchEventProvider.addCameraSwitchListener(object :
            SwitchEventProvider.CameraSwitchListener {
            override fun onCameraSwitchAvailabilityChanged(available: Boolean) {
                if (available) {
                    cameraSwitchManager.startCamera(this@SwitchifyAccessibilityService)
                } else {
                    cameraSwitchManager.stopCamera()
                }
            }
        })

        serviceScope.launch {
            NodeExaminer.examineAccessibilityTree(
                rootInActiveWindow,
                windows,
                this@SwitchifyAccessibilityService,
                this
            )
            KeyboardBridge.updateKeyboardState(windows, scanSettings)
        }

        // Update the NodeScanner with the current layout info
        serviceScope.launch {
            NodeExaminer.observeNodes().collect { nodes ->
                scanningManager.updateNodes(nodes)
            }
        }

        // Show the lock screen when the service starts
        lockScreenView.show()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        cameraSwitchManager.stopCamera()

        scanningManager.shutdown()

        lockScreenView.hide()

        Logger.logEvent("Service Unbound")

        return super.onUnbind(intent)
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
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> externalSwitchListener.onSwitchPressed(event.keyCode)
            KeyEvent.ACTION_UP -> externalSwitchListener.onSwitchReleased(event.keyCode)
            else -> false
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
}
