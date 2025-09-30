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
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.camera.CameraManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.trial.ServiceTrialManager
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * This is the main service class for the Switchify application.
 * It extends the AccessibilityService class to provide accessibility features.
 */
class SwitchifyAccessibilityService : AccessibilityService(), LifecycleOwner,
    SwitchEventProvider.CameraSwitchListener {

    private lateinit var cameraManager: CameraManager
    private lateinit var screenWatcherManager: ScreenWatcherManager
    private lateinit var scanSettings: ScanSettings
    private lateinit var techniqueEnforcer: TechniqueEnforcer
    private lateinit var deviceLockObserver: DeviceLockObserver
    private lateinit var trialManager: ServiceTrialManager
    private lateinit var startupOrchestrator: StartupOrchestrator
    private lateinit var nodeUpdateCoordinator: NodeUpdateCoordinator
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var eventPipeline: AccessibilityEventPipeline

    // Service connection for camera foreground service is encapsulated in CameraServiceController

    companion object {
        private const val TAG = "SwitchifyAccessibilityService"
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
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            deviceLockObserver = deviceLockObserver,
            serviceScope = serviceScope,
            onServiceConnected = { setupCameraServiceCallbacks() }
        )

        // Register camera manager with ServiceCore for HeadControl coordination
        ServiceCore.setCameraManager(cameraManager)
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
            // Evaluate camera state now that switches are loaded
            cameraManager.evaluateAndUpdateCameraState()
        }
    }


    /**
     * Setup callbacks for the camera service
     */
    private fun setupCameraServiceCallbacks() {
        // Set up face processing callback to pass results to CameraSwitchManager
        cameraManager.getCameraController()?.service?.setFaceResultCallback { result ->
            result?.let { faceResult ->
                // Pass the face detection result to CameraSwitchManager for gesture processing
                cameraManager.getCameraSwitchManager()?.processFaceResult(faceResult)
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
        cameraManager.cleanup()
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
        logd("Camera switch availability changed: $available")
        // Re-evaluate camera state when switches change
        cameraManager.evaluateAndUpdateCameraState()
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

                ServiceBridge.ServiceCommand.HeadControlToggled -> {
                    // Re-evaluate camera state when head control is toggled
                    cameraManager.evaluateAndUpdateCameraState()
                    ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                }

                is ServiceBridge.ServiceCommand.SetHeadControlEnabled -> {
                    val svc = ServiceCore.getHeadControlService()
                    val desired = command.enabled
                    val ok = svc?.setEnabled(desired) == true
                    if (ok) {
                        // Settings are handled by HeadControlService.setEnabled() - no duplicate needed
                        cameraManager.evaluateAndUpdateCameraState()
                        ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                    }
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
                    // Re-evaluate camera state when switches are updated
                    cameraManager.evaluateAndUpdateCameraState()
                    ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                }

                is ServiceBridge.ServiceCommand.AccessTechniqueChanged -> {
                    // Handle access technique changes and camera state evaluation
                    logd("Access technique changed to: ${command.technique}")
                    cameraManager.evaluateAndUpdateCameraState()
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
                            // Re-evaluate camera state when access technique changes
                            cameraManager.evaluateAndUpdateCameraState()
                            ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.ConfigurationUpdated)
                        }

                        PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE,
                        PreferenceManager.Keys.PREFERENCE_KEY_POINT_SCAN_LINE_SPEED_LEVEL,
                        PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SPEED_LEVEL,
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


    override val lifecycle: Lifecycle
        get() = SwitchifyLifecycleOwner.getInstance().lifecycle

    private fun logd(message: String) {
        Log.d(TAG, message)
    }
}
