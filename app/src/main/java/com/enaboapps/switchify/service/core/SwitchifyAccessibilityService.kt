package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.actions.AudioActionManager
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.switches.external.ExternalSwitchListener
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.service.camera.CameraForegroundService
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.service.utils.KeyboardBridge
import com.enaboapps.switchify.service.utils.QuickAppsManager
import com.enaboapps.switchify.service.utils.ScreenWatcher
import com.enaboapps.switchify.service.trial.ServiceTrialManager
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancelChildren

/**
 * This is the main service class for the Switchify application.
 * It extends the AccessibilityService class to provide accessibility features.
 */
class SwitchifyAccessibilityService : AccessibilityService(), LifecycleOwner,
    SwitchEventProvider.CameraSwitchListener {

    private var cameraSwitchManager: CameraSwitchManager? = null
    private lateinit var screenWatcher: ScreenWatcher
    private lateinit var scanSettings: ScanSettings
    private lateinit var deviceLockObserver: DeviceLockObserver
    private lateinit var trialManager: ServiceTrialManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Camera foreground service binding
    private var cameraService: CameraForegroundService? = null
    private var isCameraServiceBound = false
    
    // Backpressure handling for accessibility events
    private val accessibilityEventChannel = Channel<AccessibilityEvent>(capacity = Channel.CONFLATED)

    // Service connection for camera foreground service
    private val cameraServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Camera service connected")
            val binder = service as CameraForegroundService.CameraServiceBinder
            cameraService = binder.getService()
            isCameraServiceBound = true
            
            // Initialize the camera service
            if (cameraService?.initialize() == true) {
                setupCameraServiceCallbacks()
                startCameraServiceIfNeeded()
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Camera service disconnected")
            cameraService = null
            isCameraServiceBound = false
        }
    }

    companion object {
        private const val TAG = "SwitchifyAccessibilityService"
        private const val STARTUP_EXAM_DELAY_MS = 100L
        private const val QUICK_APPS_PRELOAD_DELAY_MS = 50L
        private const val CAMERA_MANAGER_INIT_DELAY_MS = 500L
    }

    override fun onCreate() {
        super.onCreate()
        deviceLockObserver = DeviceLockObserver(this)
        startAccessibilityEventProcessor()
    }

    private fun startAccessibilityEventProcessor() {
        serviceScope.launch {
            accessibilityEventChannel.consumeAsFlow()
                .flowOn(Dispatchers.Default)
                .collect { event ->
                    if (isActive) {
                        processAccessibilityEvent()
                    }
                }
        }
    }

    private fun setup() {
        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        Logger.init(this)

        IAPHandler.initialize(context = this, connectToRevenueCat = false)

        // Initialize trial manager with service shutdown callback and lock detection
        trialManager = ServiceTrialManager(
            context = this,
            onTrialExpired = {
                // Gracefully shut down the accessibility service when trial expires
                disableSelf()
            },
            deviceLockCheck = { deviceLockObserver.isUserUnlocked() }
        )

        AccessTechnique.init(this.applicationContext)

        GlobalActionManager.init(this)
        AudioActionManager.init(this)

        ServiceCore.init(this)

        val scanningManager = ServiceCore.getScanningManager() ?: return
        val externalSwitchListener = ServiceCore.getExternalSwitchListener() ?: return
        val switchEventProvider = ServiceCore.getSwitchEventProvider() ?: return

        switchEventProvider.addCameraSwitchListener(this)

        registerScreenWatcher(scanningManager, externalSwitchListener)

        scanSettings = ScanSettings(this)

        // Enforce compatible technique when launching in Directional mode
        if (scanSettings.isDirectionalScanMode()) {
            val currentTechnique = AccessTechnique.getCurrentTechnique()
            if (currentTechnique == AccessTechnique.Technique.POINT_SCAN ||
                currentTechnique == AccessTechnique.Technique.RADAR
            ) {
                AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.DIRECT_CONTROL)
            }
        }

        GestureManager.instance.setup(this)
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
            bindCameraForegroundService()
        }
    }
    
    /**
     * Bind to the camera foreground service
     */
    private fun bindCameraForegroundService() {
        val switchEventProvider = ServiceCore.getSwitchEventProvider()
        if (switchEventProvider?.hasCameraSwitch == true && !isCameraServiceBound) {
            val intent = Intent(this, CameraForegroundService::class.java)
            startService(intent) // Start the service first
            bindService(intent, cameraServiceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Binding to camera foreground service")
        }
    }
    
    /**
     * Unbind from the camera foreground service
     */
    private fun unbindCameraForegroundService() {
        if (isCameraServiceBound) {
            cameraService?.stopCamera()
            unbindService(cameraServiceConnection)
            
            // Stop the service
            val intent = Intent(this, CameraForegroundService::class.java)
            stopService(intent)
            
            isCameraServiceBound = false
            cameraService = null
            Log.d(TAG, "Unbound from camera foreground service")
        }
    }
    
    /**
     * Setup callbacks for the camera service
     */
    private fun setupCameraServiceCallbacks() {
        // Set up face processing callback to pass results to CameraSwitchManager
        cameraService?.setFaceResultCallback { result ->
            result?.let { faceResult ->
                // Pass the face detection result to CameraSwitchManager for gesture processing
                cameraSwitchManager?.processFaceResult(faceResult)
            }
        }
        
        // Optional: Setup frame processing callback if needed for UI updates
        // cameraService?.setFrameProcessingCallback { bitmap ->
        //     // Handle processed frames if needed
        // }
    }
    
    /**
     * Start camera service if conditions are met
     */
    private fun startCameraServiceIfNeeded() {
        val switchEventProvider = ServiceCore.getSwitchEventProvider()
        if (switchEventProvider?.hasCameraSwitch == true && deviceLockObserver.isUserUnlocked()) {
            cameraService?.startCamera(this)
            Log.d(TAG, "Started camera service")
        }
    }

    private fun registerScreenWatcher(scanningManager: ScanningManager, externalSwitchListener: ExternalSwitchListener) {
        screenWatcher = ScreenWatcher(
            onScreenSleep = {
                val pauseManager = ServiceCore.getPauseManager()
                if (pauseManager.isPaused) pauseManager.resume()
                GestureLockManager.instance.disableLock()
                Tasks.getInstance().checkOngoingTasks()
                externalSwitchListener.reset()
                scanningManager.reset()
            },
            onOrientationChanged = { scanningManager.reset() }
        )
        screenWatcher.register(this)
    }

    private fun unregisterScreenWatcher() {
        if (::screenWatcher.isInitialized) screenWatcher.unregister(this)
    }

    /**
     * Initializes the camera switch manager for gesture processing.
     * Camera processing is handled by CameraForegroundService.
     */
    private fun initCameraSwitchManager() {
        val scanningManager = ServiceCore.getScanningManager() ?: return
        val switchEventProvider = ServiceCore.getSwitchEventProvider() ?: return
        if (deviceLockObserver.isUserUnlocked() && switchEventProvider.hasCameraSwitch && cameraSwitchManager == null) {
            cameraSwitchManager = CameraSwitchManager(this, scanningManager, switchEventProvider)
            serviceScope.launch {
                delay(CAMERA_MANAGER_INIT_DELAY_MS)
                cameraSwitchManager?.initialize()
            }
        }
    }

    /**
     * Stops the camera switch manager if it is running.
     */
    private fun stopCameraSwitchManager() {
        if (cameraSwitchManager != null) {
            serviceScope.launch {
                cameraSwitchManager?.cleanup()
                cameraSwitchManager = null
            }
        }
    }

    /**
     * This method is called when an AccessibilityEvent is fired.
     * It uses a channel for backpressure handling to prevent overwhelming the main thread.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { accessibilityEvent ->
            accessibilityEventChannel.trySend(accessibilityEvent)
        }
    }

    /**
     * Processes accessibility events with backpressure handling.
     */
    private suspend fun processAccessibilityEvent() {
        NodeExaminer.examineAccessibilityTree(
            rootInActiveWindow,
            windows,
            this@SwitchifyAccessibilityService,
            serviceScope
        )
        KeyboardBridge.updateKeyboardState(windows, scanSettings)
    }

    override fun onInterrupt() {
        SwitchifyAccessibilityWindow.instance.cleanup()
        Logger.log(LogEvent.ServiceInterrupted)
    }

    /**
     * This method is called when the service is connected.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()

        setup()

        // Start the 1-hour trial for this service session
        trialManager.startTrial()

        Logger.log(LogEvent.ServiceConnected)

        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_START)
        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        // Stagger initialization to prevent overwhelming main thread
        serviceScope.launch { startStartupTasks() }
    }

    private suspend fun startStartupTasks() {
        // Initial accessibility tree examination
        processAccessibilityEvent()
        delay(STARTUP_EXAM_DELAY_MS)

        // Preload quick apps for faster menu access
        QuickAppsManager(this@SwitchifyAccessibilityService).preloadApps { /* Cache warmed up */ }
        delay(QUICK_APPS_PRELOAD_DELAY_MS)

        // Update the SystemNodeScanner and KeyboardScanner with the current layout info
        ServiceCore.getScanningManager()?.let { scanningManager ->
            serviceScope.launch {
                NodeExaminer.getActionableNodesFlow().collect { nodes ->
                    scanningManager.updateActionableNodes(nodes)
                }
            }
            serviceScope.launch {
                NodeExaminer.getKeyboardNodesFlow().collect { nodes ->
                    scanningManager.updateKeyboardNodes(nodes)
                }
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cameraSwitchManager?.cleanup()
        unbindCameraForegroundService()
        deviceLockObserver.stopObserving()
        unregisterScreenWatcher()
        serviceScope.coroutineContext.cancelChildren()
        ServiceCore.cleanup()
        GlobalActionManager.cleanup()
        AudioActionManager.cleanup()

        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        Logger.log(LogEvent.ServiceUnbound)

        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // Stop the trial when service is destroyed
        if (::trialManager.isInitialized) {
            trialManager.stopTrial()
        }
        
        // Unregister ScreenWatcher to prevent receiver leak
        unregisterScreenWatcher()
        serviceScope.coroutineContext.cancelChildren()
        
        SwitchifyAccessibilityWindow.instance.onServiceDestroy()
        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        SwitchifyLifecycleOwner.cleanup()
        Logger.log(LogEvent.ServiceDestroyed)
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
            bindCameraForegroundService()
        } else {
            stopCameraSwitchManager()
            unbindCameraForegroundService()
        }

        Log.d(TAG, "Camera switch availability changed: $available")
    }

    override val lifecycle: Lifecycle
        get() = SwitchifyLifecycleOwner.getInstance().lifecycle
}
