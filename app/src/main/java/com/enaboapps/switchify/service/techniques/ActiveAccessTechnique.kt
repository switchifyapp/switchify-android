package com.enaboapps.switchify.service.techniques

import android.content.Context
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.cursor.CursorManager
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.scanners.NodeScannerUI
import com.enaboapps.switchify.service.techniques.nodes.scanners.keyboard.KeyboardScanner
import com.enaboapps.switchify.service.techniques.nodes.scanners.system.SystemNodeHolder
import com.enaboapps.switchify.service.techniques.nodes.scanners.system.SystemNodeScanner
import com.enaboapps.switchify.service.techniques.radar.RadarManager
import com.enaboapps.switchify.service.utils.KeyboardBridge
import com.enaboapps.switchify.service.utils.KeyboardListener
import com.enaboapps.switchify.service.utils.ScreenWatcher
import com.enaboapps.switchify.utils.Logger

/**
 * Manages the active access technique and its state
 */
class ActiveAccessTechnique(private val context: Context) : AccessTechniqueObserver,
    KeyboardListener {
    private var cursorManager: CursorManager? = null
    private var radarManager: RadarManager? = null
    private var systemNodeScanner: SystemNodeScanner? = null
    private var keyboardScanner: KeyboardScanner? = null

    private var screenWatcher: ScreenWatcher? = null

    private var onScanningStartCallback: (() -> Unit)? = null

    init {
        AccessTechnique.observer = this
        KeyboardBridge.setKeyboardListener(this)
        screenWatcher = ScreenWatcher(onScreenSleep = {
            cleanupAll()
        })
        screenWatcher?.register(context)
    }

    val currentAccessTechnique: AccessTechniqueInterface
        get() = when {
            KeyboardBridge.isKeyboardVisible && AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.MENU -> {
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

                AccessTechnique.Technique.MENU -> MenuManager.Companion.getInstance().menuHierarchy?.getTopMenu()?.scanTree

                else -> {
                    throw IllegalStateException("Invalid access technique type: ${AccessTechnique.getCurrentTechnique()}")
                }
            }
        } as AccessTechniqueInterface

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
        Logger.logEvent("Scan method changed to: $accessTechnique")
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
        NodeScannerUI.Companion.instance.hideAll()

        if (KeyboardBridge.isKeyboardVisible) {
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

        if (!KeyboardBridge.isKeyboardVisible) {
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

        NodeScannerUI.Companion.instance.hideAll()
    }

    fun cleanupAll() {
        cursorManager?.cleanup()
        cursorManager = null
        radarManager?.cleanup()
        radarManager = null
        systemNodeScanner?.cleanup()
        systemNodeScanner = null
        cleanupKeyboard()

        SelectionHandler.cleanup()

        NodeScannerUI.Companion.instance.hideAll()
    }

    fun updateActionableNodes(nodes: List<Node>) {
        SystemNodeHolder.updateNodes(nodes)
    }

    fun updateKeyboardNodes(nodes: List<Node>) {
        ensureKeyboardScannerStarted()
        keyboardScanner?.updateNodes(nodes)
    }

    override fun onKeyboardStateChanged(isKeyboardVisible: Boolean) {
        if (isKeyboardVisible) {
            cleanupAllExceptKeyboard()
        } else {
            cleanupKeyboard()
        }
    }
}