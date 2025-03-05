package com.enaboapps.switchify.service.scanning

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.methods.cursor.CursorManager
import com.enaboapps.switchify.service.methods.nodes.Node
import com.enaboapps.switchify.service.methods.nodes.scanners.NodeScannerUI
import com.enaboapps.switchify.service.methods.nodes.scanners.keyboard.KeyboardScanner
import com.enaboapps.switchify.service.methods.nodes.scanners.system.SystemNodeHolder
import com.enaboapps.switchify.service.methods.nodes.scanners.system.SystemNodeScanner
import com.enaboapps.switchify.service.methods.radar.RadarManager
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.utils.KeyboardBridge
import com.enaboapps.switchify.service.utils.KeyboardListener
import com.enaboapps.switchify.utils.Logger

/**
 * Manages the active scanning method and its state
 */
class ActiveScanMethod(private val context: Context) : ScanMethodObserver, KeyboardListener {
    private var cursorManager: CursorManager? = null
    private var radarManager: RadarManager? = null
    private var systemNodeScanner: SystemNodeScanner? = null
    private var keyboardScanner: KeyboardScanner? = null

    private var onScanningStartCallback: (() -> Unit)? = null

    init {
        ScanMethod.observer = this
        KeyboardBridge.setKeyboardListener(this)
    }

    val currentMethod: ScanMethodBase
        get() = when {
            KeyboardBridge.isKeyboardVisible -> {
                ensureKeyboardScannerStarted()
                getKeyboardScanner().getScanTree()
            }

            ScanMethod.isInMenu -> MenuManager.getInstance().menuHierarchy?.getTopMenu()?.scanTree
            else -> when (ScanMethod.getType()) {
                ScanMethod.MethodType.CURSOR -> getCursorManager()
                ScanMethod.MethodType.RADAR -> getRadarManager()
                ScanMethod.MethodType.ITEM_SCAN -> {
                    ensureNodeScannerStarted()
                    getNodeScanner().getScanTree()
                }

                else -> {
                    throw IllegalStateException("Invalid scanning method type: ${ScanMethod.getType()}")
                }
            }
        } as ScanMethodBase

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

    override fun onScanMethodChanged(type: String) {
        cleanup(type)
        Logger.logEvent("Scan method changed to: $type")
    }

    override fun onMenuStateChanged(isInMenu: Boolean) {
        cleanup(ScanMethod.getType())

        // Start scanning if callback is set
        Handler(Looper.getMainLooper()).postDelayed({
            onScanningStartCallback?.invoke()
        }, 600)
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
        getNodeScanner().getScanTree().reset()
    }

    fun cleanup(activeType: String) {
        NodeScannerUI.instance.hideAll()

        if (KeyboardBridge.isKeyboardVisible) {
            cleanupAllExceptKeyboard()
            return
        }

        when (activeType) {
            ScanMethod.MethodType.CURSOR -> {
                radarManager?.cleanup()
                radarManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
            }

            ScanMethod.MethodType.RADAR -> {
                cursorManager?.cleanup()
                cursorManager = null
                systemNodeScanner?.cleanup()
                systemNodeScanner = null
            }

            ScanMethod.MethodType.ITEM_SCAN -> {
                cursorManager?.cleanup()
                cursorManager = null
                radarManager?.cleanup()
                radarManager = null
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

        SelectionHandler.cleanup()

        NodeScannerUI.instance.hideAll()
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