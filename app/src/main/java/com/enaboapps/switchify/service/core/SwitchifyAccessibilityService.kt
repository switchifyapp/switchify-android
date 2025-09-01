package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.actions.AudioActionManager
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.trial.ServiceTrialManager
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import com.enaboapps.switchify.switches.SwitchConfigValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * This is the main service class for the Switchify application.
 * It extends the AccessibilityService class to provide accessibility features.
 */
class SwitchifyAccessibilityService : AccessibilityService(), LifecycleOwner,
    SwitchEventProvider.CameraSwitchListener {

    private var cameraSwitchManager: CameraSwitchManager? = null
    private lateinit var screenWatcherManager: ScreenWatcherManager
    private lateinit var scanSettings: ScanSettings
    private lateinit var techniqueEnforcer: TechniqueEnforcer
    private lateinit var deviceLockObserver: DeviceLockObserver
    private lateinit var trialManager: ServiceTrialManager
    private lateinit var startupOrchestrator: StartupOrchestrator
    private lateinit var nodeUpdateCoordinator: NodeUpdateCoordinator
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var switchValidationJob: Job? = null

    // Camera foreground service binding
    private lateinit var cameraController: CameraServiceController

    private lateinit var eventPipeline: AccessibilityEventPipeline

    // Service connection for camera foreground service is encapsulated in CameraServiceController

    companion object {
        private const val TAG = "SwitchifyAccessibilityService"
        private const val CAMERA_MANAGER_INIT_DELAY_MS = 500L
    }

    override fun onCreate() {
        super.onCreate()
        deviceLockObserver = DeviceLockObserver(this)
        screenWatcherManager = ScreenWatcherManager(this)
        startupOrchestrator = StartupOrchestrator(this, serviceScope)
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

        screenWatcherManager.register(scanningManager, externalSwitchListener)

        scanSettings = ScanSettings(this)
        techniqueEnforcer = TechniqueEnforcer(scanSettings)
        nodeUpdateCoordinator = NodeUpdateCoordinator(this, serviceScope, scanSettings)
        cameraController = CameraServiceController(
            context = this,
            lifecycleOwner = this,
            deviceLockObserver = deviceLockObserver,
            onServiceConnected = { service -> setupCameraServiceCallbacks() },
            onServiceDisconnected = { /* No-op */ }
        )
        eventPipeline =
            AccessibilityEventPipeline(serviceScope) { nodeUpdateCoordinator.processAccessibilityUpdate() }
        eventPipeline.start()

        setupServiceBridge()

        techniqueEnforcer.enforceCompatibility()

        GestureManager.instance.setup(this)
        SelectionHandler.init(this)

        deviceLockObserver.startObserving(
            onUnlocked = {
                initProtectedServiceComponents()
            },
            onLocked = {
                logd("Device locked")
            })

        // Initialize components that require device unlock
        initProtectedServiceComponents()
    }

    /**
     * Initializes protected service components if the device is unlocked.
     */
    private fun initProtectedServiceComponents() {
        if (deviceLockObserver.isUserUnlocked()) {
            logd("Device unlocked, initializing protected components")
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
        cameraController.bindIfNeeded()
    }

    /**
     * Unbind from the camera foreground service
     */
    private fun unbindCameraForegroundService() {
        cameraController.unbindIfBound()
    }

    /**
     * Setup callbacks for the camera service
     */
    private fun setupCameraServiceCallbacks() {
        // Set up face processing callback to pass results to CameraSwitchManager
        cameraController.service?.setFaceResultCallback { result ->
            result?.let { faceResult ->
                // Pass the face detection result to CameraSwitchManager for gesture processing
                cameraSwitchManager?.processFaceResult(faceResult)
            }
        }
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
            eventPipeline.trySend(accessibilityEvent)
        }
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
        serviceScope.launch {
            startupOrchestrator.executeStartupTasks { nodeUpdateCoordinator.processAccessibilityUpdate() }
        }
    }


    override fun onUnbind(intent: Intent?): Boolean {
        cameraSwitchManager?.cleanup()
        unbindCameraForegroundService()
        deviceLockObserver.stopObserving()
        screenWatcherManager.unregister()
        serviceScope.coroutineContext.cancelChildren()
        eventPipeline.stop()
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
        screenWatcherManager.unregister()
        serviceScope.coroutineContext.cancelChildren()
        eventPipeline.stop()

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

        logd("Camera switch availability changed: $available")
    }


    /**
     * Sets up ServiceBridge for app-to-service communication.
     */
    private fun setupServiceBridge() {
        ServiceBridge.serviceCommands
            .onEach { command ->
                handleServiceCommand(command)
            }
            .launchIn(serviceScope)

        serviceScope.launch {
            ServiceBridge.serviceEvents.collect { event ->
                if (event is ServiceBridge.ServiceEvent.SwitchEventsUpdated) {
                    switchValidationJob?.cancel()
                    switchValidationJob = launch {
                        delay(5000)
                        validateSwitchConfigurationAndHandle()
                    }
                }
            }
        }

        // Notify app that service is ready
        ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ServiceReady)
    }

    /**
     * Handles commands received from the app UI via ServiceBridge.
     */
    private fun handleServiceCommand(command: ServiceBridge.ServiceCommand) {
        try {
            when (command) {
                ServiceBridge.ServiceCommand.EnforceTechniqueCompatibility -> {
                    techniqueEnforcer.enforceCompatibility()
                    ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                }

                ServiceBridge.ServiceCommand.ReloadSettings -> {
                    AccessTechnique.reloadFromPreferences()
                    techniqueEnforcer.enforceCompatibility()
                    ServiceCore.getScanningManager()?.reset()
                    ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                }

                ServiceBridge.ServiceCommand.ClearCache -> {
                    // Clear any relevant caches
                    serviceScope.launch {
                        nodeUpdateCoordinator.processAccessibilityUpdate()
                    }
                    ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                }

                ServiceBridge.ServiceCommand.UpdateSwitches -> {
                    // Trigger switch event provider update
                    ServiceCore.getSwitchEventProvider()?.reload()
                    ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                }

                is ServiceBridge.ServiceCommand.UpdateConfiguration -> {
                    // Handle specific configuration updates
                    when (command.key) {
                        PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE -> {
                            // Scan mode changed - enforce technique compatibility
                            techniqueEnforcer.enforceCompatibility()
                            ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                        }

                        PreferenceManager.Keys.PREFERENCE_KEY_ACCESS_TECHNIQUE -> {
                            // Access technique changed - reload from preferences and enforce compatibility
                            AccessTechnique.reloadFromPreferences()
                            techniqueEnforcer.enforceCompatibility()
                            ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                        }

                        PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE,
                        PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_FINE_SCAN_RATE,
                        PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SCAN_RATE,
                        PreferenceManager.Keys.PREFERENCE_KEY_SCAN_RATE -> {
                            // Scan rate settings changed - service will pick up new values automatically
                            ServiceCore.getScanningManager()?.reset()
                            ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                        }

                        else -> {
                            logd("Configuration updated: ${command.key}")
                            ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logd("Error handling service command: ${e.message}")
            ServiceBridge.emitEvent(
                ServiceBridge.ServiceEvent.ServiceError("Command handling failed: ${e.message}")
            )
        }
    }

    private fun validateSwitchConfigurationAndHandle() {
        try {
            val validator = SwitchConfigValidator(this)
            if (!validator.isConfigurationValid()) {
                ServiceMessageHUD.instance.showMessage(
                    R.string.hud_switch_config_invalid,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.MEDIUM
                )
                serviceScope.launch {
                    delay(5000)
                    val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    if (intent != null) startActivity(intent)
                }
            }
        } catch (_: Exception) { }
    }

    override val lifecycle: Lifecycle
        get() = SwitchifyLifecycleOwner.getInstance().lifecycle

    private fun logd(message: String) {
        Log.d(TAG, message)
    }
}
