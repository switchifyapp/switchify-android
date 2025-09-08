package com.enaboapps.switchify.service.techniques

import android.content.Context
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.keyboard.KeyboardStateListener
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.directcontrol.DirectControlManager
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlManager
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
    private var directControlManager: DirectControlManager? = null
    private var headControlManager: HeadControlManager? = null
    private var keyboardScanner: KeyboardScanner? = null

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
                AccessTechnique.Technique.DIRECT_CONTROL -> getDirectControlManager()
                AccessTechnique.Technique.HEAD_CONTROL -> getHeadControlManager()
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
                    throw IllegalStateException("Invalid access technique type: ${AccessTechnique.getCurrentTechnique()}")
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
        cleanup(accessTechnique)
        // Trigger initialization of the new technique by accessing currentAccessTechnique
        try {
            currentAccessTechnique
        } catch (e: Exception) {
            android.util.Log.w("ActiveAccessTechnique", "Failed to initialize technique: $accessTechnique", e)
        }
        // Use ServiceBridge for camera evaluation instead of direct calls
        ServiceBridge.sendCommand(ServiceBridge.ServiceCommand.AccessTechniqueChanged(accessTechnique))
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

    private fun getDirectControlManager(): DirectControlManager {
        if (directControlManager == null) {
            directControlManager = DirectControlManager(context)
        }
        return directControlManager!!
    }

    private fun getHeadControlManager(): HeadControlManager {
        if (headControlManager == null) {
            headControlManager = HeadControlManager(context)
        }
        return headControlManager!!
    }

    fun getNodeScanner(): SystemNodeScanner {
        ensureNodeScannerStarted()
        return systemNodeScanner!!
    }

    private fun getKeyboardScanner(): KeyboardScanner {
        ensureKeyboardScannerStarted()
        return keyboardScanner!!
    }

    fun getHeadControlManagerInstance(): HeadControlManager {
        return getHeadControlManager()
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
                directControlManager?.cleanup()
                directControlManager = null
                headControlManager?.cleanup()
                headControlManager = null
            }

            AccessTechnique.Technique.RADAR -> {
                pointScanManager?.cleanup()
                pointScanManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
                directControlManager?.cleanup()
                directControlManager = null
                headControlManager?.cleanup()
                headControlManager = null
            }

            AccessTechnique.Technique.ITEM_SCAN -> {
                pointScanManager?.cleanup()
                pointScanManager = null
                radarManager?.cleanup()
                radarManager = null
                directControlManager?.cleanup()
                directControlManager = null
                headControlManager?.cleanup()
                headControlManager = null
            }

            AccessTechnique.Technique.MENU -> {
                pointScanManager?.cleanup()
                pointScanManager = null
                radarManager?.cleanup()
                radarManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
                directControlManager?.cleanup()
                directControlManager = null
                headControlManager?.cleanup()
                headControlManager = null
            }

            AccessTechnique.Technique.DIRECT_CONTROL -> {
                pointScanManager?.cleanup()
                pointScanManager = null
                radarManager?.cleanup()
                radarManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
                headControlManager?.cleanup()
                headControlManager = null
            }

            AccessTechnique.Technique.HEAD_CONTROL -> {
                pointScanManager?.cleanup()
                pointScanManager = null
                radarManager?.cleanup()
                radarManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
                directControlManager?.cleanup()
                directControlManager = null
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
        directControlManager?.cleanup()
        directControlManager = null
        headControlManager?.cleanup()
        headControlManager = null

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
        directControlManager?.cleanup()
        directControlManager = null
        headControlManager?.cleanup()
        headControlManager = null
        cleanupKeyboard()

        // Unregister ScreenWatcher to prevent receiver leak
        screenWatcher?.unregister(context)
        screenWatcher = null

        SelectionHandler.cleanup()

        NodeScannerUI.instance.hideAll()
    }

    /**
     * Cleanup method to be called when ActiveAccessTechnique is no longer needed.
     * This ensures proper cleanup of all resources including ScreenWatcher.
     */
    fun destroy() {
        cleanupAll()
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
