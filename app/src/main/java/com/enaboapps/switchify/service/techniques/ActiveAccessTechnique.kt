package com.enaboapps.switchify.service.techniques

import android.content.Context
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.keyboard.KeyboardStateListener
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.scanners.NodeScannerUI
import com.enaboapps.switchify.service.techniques.nodes.scanners.keyboard.KeyboardScanner
import com.enaboapps.switchify.service.techniques.nodes.scanners.system.SystemNodeHolder
import com.enaboapps.switchify.service.techniques.nodes.scanners.system.SystemNodeScanner
import com.enaboapps.switchify.service.techniques.pointscan.PointScanManager
import com.enaboapps.switchify.service.techniques.radar.RadarManager
import com.enaboapps.switchify.service.utils.ScreenWatcher

/**
 * Manages the active access technique and its state
 */
class ActiveAccessTechnique(private val context: Context) : AccessTechniqueObserver,
    KeyboardStateListener {
    private var pointScanManager: PointScanManager? = null
    private var radarManager: RadarManager? = null
    private var systemNodeScanner: SystemNodeScanner? = null
    private var keyboardScanner: KeyboardScanner? = null

    // Track the technique that was active before switching to MENU
    private var underlyingTechnique: String? = null

    private var screenWatcher: ScreenWatcher? = null

    private var onScanningStartCallback: (() -> Unit)? = null

    init {
        AccessTechnique.observer = this
        KeyboardManager.initialize()
        KeyboardManager.setKeyboardStateListener(this)
        screenWatcher = ScreenWatcher(onScreenSleep = {
            cleanupAll()
        })
        screenWatcher?.register(context)
    }

    val currentAccessTechnique: AccessTechniqueInterface
        get() = when {
            KeyboardManager.isKeyboardVisible() && !KeyboardManager.isEscapedFromKeyboard() && AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.MENU -> {
                ensureKeyboardScannerStarted()
                getKeyboardScanner().scanTree
            }

            else -> when (AccessTechnique.getCurrentTechnique()) {
                AccessTechnique.Technique.POINT_SCAN -> getPointScanManager()
                AccessTechnique.Technique.RADAR -> getRadarManager()
                AccessTechnique.Technique.ITEM_SCAN -> {
                    ensureNodeScannerStarted()
                    getNodeScanner().scanTree
                }

                AccessTechnique.Technique.MENU -> {
                    val menuHierarchy = MenuManager.getInstance().menuHierarchy
                    val topMenu = menuHierarchy?.getTopMenu()
                    topMenu?.scanTree ?: getPointScanManager()
                }

                else -> {
                    // Default fallback to point scan for unknown techniques
                    getPointScanManager()
                }
            }
        }

    private fun ensureNodeScannerStarted() {
        if (systemNodeScanner == null) {
            systemNodeScanner = SystemNodeScanner()
            systemNodeScanner?.start(context)
        }
    }

    private fun ensureKeyboardScannerStarted() {
        if (keyboardScanner == null) {
            keyboardScanner = KeyboardScanner()
            keyboardScanner?.start(context)
        }
    }

    override fun onAccessTechniqueChanged(accessTechnique: String) {
        // Track the underlying technique when switching to MENU
        if (accessTechnique == AccessTechnique.Technique.MENU && underlyingTechnique == null) {
            // Store the technique from preferences as the underlying technique
            underlyingTechnique =
                AccessTechnique.getStoredTechnique() ?: AccessTechnique.Technique.ITEM_SCAN
        } else if (accessTechnique != AccessTechnique.Technique.MENU) {
            // Clear underlying technique when not in menu mode
            underlyingTechnique = null
        }

        cleanup(accessTechnique)
        // Trigger initialization of the new technique by accessing currentAccessTechnique
        try {
            currentAccessTechnique
        } catch (e: Exception) {
            android.util.Log.w(
                "ActiveAccessTechnique",
                "Failed to initialize technique: $accessTechnique",
                e
            )
        }
        // Use ServiceBridge for camera evaluation instead of direct calls
        ServiceBridge.sendCommand(
            ServiceBridge.ServiceCommand.AccessTechniqueChanged(
                accessTechnique
            )
        )
    }

    fun setOnScanningStartCallback(callback: () -> Unit) {
        onScanningStartCallback = callback
    }

    private fun getPointScanManager(): PointScanManager {
        if (pointScanManager == null) {
            pointScanManager = PointScanManager(context)
        }
        return pointScanManager!!
    }

    private fun getRadarManager(): RadarManager {
        if (radarManager == null) {
            radarManager = RadarManager(context)
        }
        return radarManager!!
    }


    fun getNodeScanner(): SystemNodeScanner {
        ensureNodeScannerStarted()
        return systemNodeScanner!!
    }

    private fun getKeyboardScanner(): KeyboardScanner {
        ensureKeyboardScannerStarted()
        return keyboardScanner!!
    }


    fun resetNodeScanner() {
        getNodeScanner().scanTree.stopScanningAndReset()
    }

    fun cleanup(currentTechnique: String) {
        NodeScannerUI.instance.hideAll()

        if (KeyboardManager.isKeyboardVisible()) {
            cleanupAllExceptKeyboard()
            return
        }

        when (currentTechnique) {
            AccessTechnique.Technique.POINT_SCAN -> {
                radarManager?.cleanup()
                radarManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
            }

            AccessTechnique.Technique.RADAR -> {
                pointScanManager?.cleanup()
                pointScanManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
            }

            AccessTechnique.Technique.ITEM_SCAN -> {
                pointScanManager?.cleanup()
                pointScanManager = null
                radarManager?.cleanup()
                radarManager = null
            }

            AccessTechnique.Technique.MENU -> {
                pointScanManager?.cleanup()
                pointScanManager = null
                radarManager?.cleanup()
                radarManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
            }


        }

        if (!KeyboardManager.isKeyboardVisible()) {
            cleanupKeyboard()
        }

        SelectionHandler.cleanup()
    }

    private fun cleanupKeyboard() {
        keyboardScanner?.cleanup()
        keyboardScanner = null
    }

    private fun cleanupAllExceptKeyboard() {
        radarManager?.cleanup()
        radarManager = null
        systemNodeScanner?.cleanup()
        systemNodeScanner = null
        pointScanManager?.cleanup()
        pointScanManager = null
        SelectionHandler.cleanup()

        NodeScannerUI.instance.hideAll()
    }

    fun cleanupAll() {
        pointScanManager?.cleanup()
        pointScanManager = null
        radarManager?.cleanup()
        radarManager = null
        systemNodeScanner?.cleanup()
        systemNodeScanner = null
        cleanupKeyboard()

        SelectionHandler.cleanup()

        NodeScannerUI.instance.hideAll()
    }

    /**
     * Cleanup method to be called when ActiveAccessTechnique is no longer needed.
     * This ensures proper cleanup of all resources including ScreenWatcher.
     */
    fun destroy() {
        cleanupAll()
        // Unregister ScreenWatcher to prevent receiver leak
        screenWatcher?.unregister(context)
        screenWatcher = null
        AccessTechnique.observer = null
        KeyboardManager.removeKeyboardStateListener()
    }

    fun updateActionableNodes(nodes: List<Node>) {
        SystemNodeHolder.updateNodes(nodes)
    }

    fun updateKeyboardNodes(nodes: List<Node>) {
        ensureKeyboardScannerStarted()
        keyboardScanner?.updateNodes(nodes)
    }

    override fun onKeyboardStateChanged(
        isKeyboardVisible: Boolean,
        isEscapedFromKeyboard: Boolean
    ) {
        if (isKeyboardVisible) {
            cleanupAllExceptKeyboard()
        } else {
            cleanupKeyboard()
        }
    }
}
