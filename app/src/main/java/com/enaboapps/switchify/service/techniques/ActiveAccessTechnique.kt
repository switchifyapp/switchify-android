package com.enaboapps.switchify.service.techniques

import android.content.Context
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.cursor.CursorManager
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.scanners.NodeScannerUI
import com.enaboapps.switchify.service.techniques.nodes.scanners.keyboard.KeyboardScanner
import com.enaboapps.switchify.service.techniques.nodes.scanners.system.SystemNodeHolder
import com.enaboapps.switchify.service.techniques.nodes.scanners.system.SystemNodeScanner
import com.enaboapps.switchify.service.techniques.radar.RadarManager
import com.enaboapps.switchify.service.keyboard.KeyboardStateListener
import com.enaboapps.switchify.service.utils.ScreenWatcher

/**
 * Manages the active access technique and its state
 */
class ActiveAccessTechnique(private val context: Context) : AccessTechniqueObserver,
    KeyboardStateListener {
    private var cursorManager: CursorManager? = null
    private var radarManager: RadarManager? = null
    private var systemNodeScanner: SystemNodeScanner? = null
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
                AccessTechnique.Technique.CURSOR -> getCursorManager()
                AccessTechnique.Technique.RADAR -> getRadarManager()
                AccessTechnique.Technique.ITEM_SCAN -> {
                    ensureNodeScannerStarted()
                    getNodeScanner().scanTree
                }

                AccessTechnique.Technique.MENU -> {
                    val menuHierarchy = MenuManager.getInstance().menuHierarchy
                    val topMenu = menuHierarchy?.getTopMenu()
                    topMenu?.scanTree ?: getCursorManager()
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
    }

    fun setOnScanningStartCallback(callback: () -> Unit) {
        onScanningStartCallback = callback
    }

    private fun getCursorManager(): CursorManager {
        if (cursorManager == null) {
            cursorManager = CursorManager(context)
        }
        return cursorManager!!
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
            AccessTechnique.Technique.CURSOR -> {
                radarManager?.cleanup()
                radarManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
            }

            AccessTechnique.Technique.RADAR -> {
                cursorManager?.cleanup()
                cursorManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
            }

            AccessTechnique.Technique.ITEM_SCAN -> {
                cursorManager?.cleanup()
                cursorManager = null
                radarManager?.cleanup()
                radarManager = null
            }

            AccessTechnique.Technique.MENU -> {
                cursorManager?.cleanup()
                cursorManager = null
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
        cursorManager?.cleanup()
        cursorManager = null

        SelectionHandler.cleanup()

        NodeScannerUI.instance.hideAll()
    }

    fun cleanupAll() {
        cursorManager?.cleanup()
        cursorManager = null
        radarManager?.cleanup()
        radarManager = null
        systemNodeScanner?.cleanup()
        systemNodeScanner = null
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
        KeyboardManager.setKeyboardStateListener(null)
    }

    fun updateActionableNodes(nodes: List<Node>) {
        SystemNodeHolder.updateNodes(nodes)
    }

    fun updateKeyboardNodes(nodes: List<Node>) {
        ensureKeyboardScannerStarted()
        keyboardScanner?.updateNodes(nodes)
    }

    override fun onKeyboardStateChanged(isKeyboardVisible: Boolean, isEscapedFromKeyboard: Boolean) {
        if (isKeyboardVisible) {
            cleanupAllExceptKeyboard()
        } else {
            cleanupKeyboard()
        }
    }
}