package com.enaboapps.switchify.service.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.backend.data.FileManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.actions.AudioActionManager
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.camera.CameraManager
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.pc.PcMouseRepeatManager
import com.enaboapps.switchify.pc.PcServiceConnectionController
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.stats.StatsCollector
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.trial.ServiceTrialManager
import com.enaboapps.switchify.service.trial.ServiceTrialOverlay
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.service.window.ServiceStartupSplash
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.utils.CrashReporter
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
    private lateinit var trialOverlay: ServiceTrialOverlay
    private lateinit var startupOrchestrator: StartupOrchestrator
    private lateinit var nodeUpdateCoordinator: NodeUpdateCoordinator
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var protectedStorageMigrationAttempted = false
    private val adbTestingBridgeReceiver = AdbTestingBridgeReceiver()
    private var adbTestingBridgeRegistered = false

    private lateinit var eventPipeline: AccessibilityEventPipeline

    /**
     * Provides access to the service's coroutine scope for lifecycle-aware asynchronous operations.
     *
     * @return The CoroutineScope tied to this service's lifecycle.
     */
    fun getServiceScope(): CoroutineScope = serviceScope

    // Service connection for camera foreground service is encapsulated in CameraServiceController

    companion object {
        private const val TAG = "SwitchifyAccessibilityService"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize StatsCollector early to ensure it's ready before switches are pressed
        StatsCollector.getInstance().initialize(this)

        deviceLockObserver = DeviceLockObserver(this)
        screenWatcherManager = ScreenWatcherManager(this)
        startupOrchestrator = StartupOrchestrator(this, serviceScope)
    }

    private fun setup() {
        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

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

        // Initialize trial overlay to show countdown for non-Pro users
        trialOverlay = ServiceTrialOverlay(this, trialManager)

        AccessTechnique.init(this.applicationContext)

        GlobalActionManager.init(this)
        AudioActionManager.init(this)

        ServiceCore.init(this)

        val scanningManager = ServiceCore.getScanningManager() ?: return
        val externalSwitchListener = ServiceCore.getExternalSwitchListener() ?: return
        val switchEventProvider = ServiceCore.getSwitchEventProvider() ?: return

        screenWatcherManager.register(scanningManager, externalSwitchListener)

        scanSettings = ScanSettings(this)
        techniqueEnforcer = TechniqueEnforcer(scanSettings)
        nodeUpdateCoordinator = NodeUpdateCoordinator(this, scanSettings)
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            deviceLockObserver = deviceLockObserver,
            serviceScope = serviceScope,
            onServiceConnected = { setupCameraServiceCallbacks() }
        )

        ServiceCore.setCameraManager(cameraManager)
        switchEventProvider.addCameraSwitchListener(this)
        eventPipeline =
            AccessibilityEventPipeline(serviceScope) { nodeUpdateCoordinator.processAccessibilityUpdate() }
        eventPipeline.start()

        setupServiceBridge()
        registerAdbTestingBridgeIfNeeded()

        // Observe Pro status changes to hide overlay when user upgrades
        serviceScope.launch {
            IAPHandler.customerInfo.collect { customerInfo ->
                if (IAPHandler.isPro() && ::trialOverlay.isInitialized) {
                    trialOverlay.hideOverlay()
                    trialOverlay.stopUpdates()
                }
            }
        }

        techniqueEnforcer.enforceCompatibility()

        val gestureTargetIndicator = ServiceCore.getGestureTargetIndicator() ?: return
        GestureManager.instance.setup(this, gestureTargetIndicator)
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
            migrateToProtectedStorageIfUnlocked()
            CrashReporter.enqueueUpload(this)
            IAPHandler.connect(context = this)
            startTrialOverlayIfNeeded()
            cameraManager.evaluateAndUpdateCameraState()
            initPcServiceConnectionControllerIfNeeded()
            val statsCollector = StatsCollector.getInstance()
            statsCollector.ensureInitialized()
            serviceScope.launch {
                statsCollector.forceFlush()
            }
        }
    }

    private fun migrateToProtectedStorageIfUnlocked() {
        if (protectedStorageMigrationAttempted || !deviceLockObserver.isUserUnlocked()) return
        protectedStorageMigrationAttempted = true

        try {
            PreferenceManager(this).migrateToProtectedStorage()
        } catch (e: Exception) {
            Logger.log(
                LogEvent.AppSetupStageFailed,
                data = mapOf(
                    "result" to "failure",
                    "stage" to "service_preferences_migration",
                    "reason" to "exception"
                ),
                throwable = e
            )
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                FileManager.create(this@SwitchifyAccessibilityService)
                    .migrateFromRegularStorage(this@SwitchifyAccessibilityService)
                ServiceCore.getSwitchEventProvider()?.reload("protected_storage_migrated")
            } catch (e: Exception) {
                Logger.log(
                    LogEvent.AppSetupStageFailed,
                    data = mapOf(
                        "result" to "failure",
                        "stage" to "service_file_migration",
                        "reason" to "exception"
                    ),
                    throwable = e
                )
            }
        }
    }

    private fun startTrialOverlayIfNeeded() {
        if (!deviceLockObserver.isUserUnlocked()) return
        trialManager.startTrial()
        if (trialManager.isTrialActive() && !IAPHandler.isPro()) {
            trialOverlay.showOverlay()
            trialOverlay.startUpdates()
        }
    }

    private fun initPcServiceConnectionControllerIfNeeded() {
        if (ServiceCore.getPcServiceConnectionController() != null) return
        ServiceCore.setPcServiceConnectionController(PcServiceConnectionController.getInstance(this))
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

    private fun registerAdbTestingBridgeIfNeeded() {
        if (!BuildConfig.DEBUG || adbTestingBridgeRegistered) return
        ContextCompat.registerReceiver(
            this,
            adbTestingBridgeReceiver,
            IntentFilter(AdbTestingBridgeReceiver.ACTION_PERFORM_SWITCH_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
        adbTestingBridgeRegistered = true
    }

    private fun unregisterAdbTestingBridgeIfNeeded() {
        if (!adbTestingBridgeRegistered) return
        runCatching { unregisterReceiver(adbTestingBridgeReceiver) }
        adbTestingBridgeRegistered = false
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

    fun refreshAccessibilityNodes() {
        serviceScope.launch {
            nodeUpdateCoordinator.processAccessibilityUpdate()
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

        ServiceStartupSplash.instance.show()

        startTrialOverlayIfNeeded()

        Logger.log(LogEvent.ServiceConnected)

        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_START)
        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Stagger initialization to prevent overwhelming main thread
        serviceScope.launch {
            startupOrchestrator.executeStartupTasks { nodeUpdateCoordinator.processAccessibilityUpdate() }
        }
    }


    override fun onUnbind(intent: Intent?): Boolean {
        if (::cameraManager.isInitialized) {
            cameraManager.cleanup()
        }
        deviceLockObserver.stopObserving()
        screenWatcherManager.unregister()
        unregisterAdbTestingBridgeIfNeeded()
        serviceScope.coroutineContext.cancelChildren()
        if (::eventPipeline.isInitialized) {
            eventPipeline.stop()
        }
        ServiceCore.cleanup()
        SwitchifyAccessibilityWindow.instance.onServiceDestroy()
        PcMouseRepeatManager.instance.clearServiceState()
        GestureRepeatManager.instance.clearServiceState()
        GestureLockManager.instance.clearServiceState()
        GlobalActionManager.cleanup()
        AudioActionManager.cleanup()

        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        Logger.log(LogEvent.ServiceUnbound)

        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // Hide and stop trial overlay updates
        if (::trialOverlay.isInitialized) {
            trialOverlay.hideOverlay()
            trialOverlay.stopUpdates()
        }

        // Stop the trial when service is destroyed
        if (::trialManager.isInitialized) {
            trialManager.stopTrial()
        }

        // Unregister ScreenWatcher to prevent receiver leak
        screenWatcherManager.unregister()
        unregisterAdbTestingBridgeIfNeeded()
        serviceScope.coroutineContext.cancelChildren()
        if (::eventPipeline.isInitialized) {
            eventPipeline.stop()
        }

        SwitchifyAccessibilityWindow.instance.onServiceDestroy()
        PcMouseRepeatManager.instance.clearServiceState()
        GestureRepeatManager.instance.clearServiceState()
        GestureLockManager.instance.clearServiceState()
        SwitchifyLifecycleOwner.getInstance().handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        SwitchifyLifecycleOwner.cleanup()

        // Flush any pending stats before service is destroyed
        // Use runBlocking to ensure flush completes before service destruction
        try {
            kotlinx.coroutines.runBlocking {
                StatsCollector.getInstance().forceFlush()
            }
            Log.d(TAG, "Stats flushed successfully on service destroy")
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing stats on service destroy", e)
        }

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
        if (!::cameraManager.isInitialized) return
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
        val commandName = command::class.java.simpleName
        var result = "success"
        var reason: String? = null
        var commandKey: String? = null

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
                    } else {
                        result = "skipped"
                        reason = "head_control_not_ready"
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
                    commandKey = command.key
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

                is ServiceBridge.ServiceCommand.PerformSwitchActionForTesting -> {
                    commandKey = command.actionId.toString()
                    if (!BuildConfig.DEBUG) {
                        result = "skipped"
                        reason = "not_debug_build"
                    } else {
                        val scanningManager = ServiceCore.getScanningManager()
                        if (scanningManager == null) {
                            result = "skipped"
                            reason = "scanning_manager_not_ready"
                        } else {
                            scanningManager.performAction(SwitchAction(command.actionId))
                        }
                    }
                }
            }

            Logger.log(
                LogEvent.ServiceCommandHandled,
                data = mapOf(
                    "result" to result,
                    "reason" to reason,
                    "command" to commandName,
                    "key" to commandKey
                )
            )
        } catch (e: Exception) {
            Logger.log(
                LogEvent.ServiceCommandFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "exception",
                    "command" to commandName,
                    "key" to commandKey
                ),
                throwable = e
            )
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
