package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.KeyboardEscapePrompt
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.scanning.tree.ScanTreeCallback
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Base class for node scanners that provides common functionality for both system and keyboard scanners.
 * Implements improved handling of rapid updates using multiple detection windows.
 */
abstract class BaseNodeScanner : ScanTreeCallback {
    protected lateinit var context: Context
    lateinit var scanTree: ScanTree
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var revertToCursorJob: Job? = null

    // Multi-window update detection
    private val shortWindowUpdates = ArrayDeque<Long>(SHORT_WINDOW_SIZE)
    private val longWindowUpdates = ArrayDeque<Long>(LONG_WINDOW_SIZE)
    private var rapidUpdateCheckJob: Job? = null
    private var warningCount = 0
    private var lastWarningTime = 0L

    // Duplicate update detection
    private var lastUpdateNodes: List<Node>? = null
    private var lastUpdateTime = 0L
    private val DUPLICATE_UPDATE_THRESHOLD_MS = 100L // Minimum time between updates

    companion object {
        private const val TAG = "BaseNodeScanner"
        private const val EMPTY_NODES_TIMEOUT_MS = 5000L

        // Short window for burst detection
        private const val SHORT_WINDOW_MS = 5000L
        private const val SHORT_WINDOW_SIZE = 200
        private const val SHORT_WINDOW_THRESHOLD = 100

        // Long window for sustained update detection
        private const val LONG_WINDOW_MS = 30000L
        private const val LONG_WINDOW_SIZE = 2000
        private const val LONG_WINDOW_THRESHOLD = 1000

        // Warning system
        private const val WARNING_THRESHOLD = 3
        private const val WARNING_RESET_MS = 60000L // 1 minute
        private const val UPDATE_CHECK_INTERVAL_MS = 1000L
    }

    open fun start(context: Context) {
        this.context = context
        startTimeoutToRevertToCursor()
        scanTree = ScanTree(
            context = context,
            stopScanningOnSelect = true,
            hasCycleBreak = KeyboardManager.shouldEnableCycleBreak(),
            callback = this
        )
        startRapidUpdateCheck()
    }

    open fun updateNodes(nodes: List<Node>) {
        val currentTime = System.currentTimeMillis()

        // Check for duplicate updates
        if (isDuplicateUpdate(nodes, currentTime)) {
            Log.d(TAG, "Skipping duplicate update")
            return
        }

        buildFromNodes(nodes)
        recordUpdateTimestamp()
        lastUpdateNodes = nodes
        lastUpdateTime = currentTime

        if (nodes.isEmpty()) {
            startTimeoutToRevertToCursor()
        } else {
            stopTimeoutToRevertToCursor()
        }
    }

    private fun isDuplicateUpdate(nodes: List<Node>, currentTime: Long): Boolean {
        // Check if we're getting updates too quickly
        if (currentTime - lastUpdateTime < DUPLICATE_UPDATE_THRESHOLD_MS) {
            return true
        }

        // Check if the node lists are identical
        val lastNodes = lastUpdateNodes
        if (lastNodes != null && lastNodes.size == nodes.size) {
            // Compare node lists by their unique identifiers or properties
            return lastNodes.zip(nodes).all { (old, new) ->
                old.getLeft() == new.getLeft() &&
                        old.getTop() == new.getTop() &&
                        old.getWidth() == new.getWidth() &&
                        old.getHeight() == new.getHeight()
            }
        }

        return false
    }

    open fun cleanup() {
        revertToCursorJob?.cancel()
        rapidUpdateCheckJob?.cancel()
        shortWindowUpdates.clear()
        longWindowUpdates.clear()
        warningCount = 0
        if (::scanTree.isInitialized) {
            scanTree.cleanup()
        }
    }

    private fun recordUpdateTimestamp() {
        val currentTime = System.currentTimeMillis()

        // Record in both windows
        shortWindowUpdates.addLast(currentTime)
        longWindowUpdates.addLast(currentTime)

        // Maintain window sizes
        while (shortWindowUpdates.size > SHORT_WINDOW_SIZE) {
            shortWindowUpdates.removeFirst()
        }
        while (longWindowUpdates.size > LONG_WINDOW_SIZE) {
            longWindowUpdates.removeFirst()
        }
    }

    private fun startRapidUpdateCheck() {
        rapidUpdateCheckJob?.cancel()
        rapidUpdateCheckJob = coroutineScope.launch {
            while (isActive) {
                checkForRapidUpdates()
                delay(UPDATE_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun checkForRapidUpdates() {
        val currentTime = System.currentTimeMillis()

        // Reset warning count if enough time has passed
        if (currentTime - lastWarningTime > WARNING_RESET_MS) {
            warningCount = 0
        }

        // Clean up old timestamps
        cleanupWindows(currentTime)

        // Check both windows for violations
        val shortWindowViolation = shortWindowUpdates.size >= SHORT_WINDOW_THRESHOLD
        val longWindowViolation = longWindowUpdates.size >= LONG_WINDOW_THRESHOLD

        if (shortWindowViolation || longWindowViolation) {
            handleUpdateViolation()
        }
    }

    private fun cleanupWindows(currentTime: Long) {
        // Clean short window
        while (shortWindowUpdates.isNotEmpty()) {
            val first = shortWindowUpdates.firstOrNull()
            if (first != null && first < currentTime - SHORT_WINDOW_MS) {
                shortWindowUpdates.removeFirst()
            } else {
                break
            }
        }

        // Clean long window
        while (longWindowUpdates.isNotEmpty()) {
            val first = longWindowUpdates.firstOrNull()
            if (first != null && first < currentTime - LONG_WINDOW_MS) {
                longWindowUpdates.removeFirst()
            } else {
                break
            }
        }
    }

    private fun handleUpdateViolation() {
        warningCount++
        lastWarningTime = System.currentTimeMillis()

        if (warningCount >= WARNING_THRESHOLD) {
            switchToCursorMode("persistent rapid updates")
            warningCount = 0
            shortWindowUpdates.clear()
            longWindowUpdates.clear()
        } else {
            Log.d(TAG, "Rapid update warning $warningCount/$WARNING_THRESHOLD")
        }
    }

    open fun startTimeoutToRevertToCursor() {
        revertToCursorJob?.cancel()
        revertToCursorJob = coroutineScope.launch {
            delay(EMPTY_NODES_TIMEOUT_MS)
            if (::scanTree.isInitialized && scanTree.isEmpty()) {
                switchToCursorMode("empty nodes")
            }
        }
    }

    private fun stopTimeoutToRevertToCursor() {
        revertToCursorJob?.cancel()
        revertToCursorJob = null
    }

    protected open fun buildFromNodes(nodes: List<Node>) {
        if (::scanTree.isInitialized) {
            scanTree.buildTree(nodes)
        }
    }

    private fun switchToCursorMode(reason: String) {
        coroutineScope.launch(Dispatchers.Main) {
            if (::scanTree.isInitialized) {
                scanTree.stopScanningAndReset()
            }
            if (AccessTechnique.getCurrentTechnique() == AccessTechnique.Technique.ITEM_SCAN) {
                AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.POINT_SCAN)
                Log.d(TAG, "Switched to point scan mode due to $reason")
            }
        }
    }

    // ScanTreeCallback implementation
    override fun onScanTreeCycleBreakStarted() {
        Log.d(TAG, "Cycle break started")
        KeyboardEscapePrompt.instance.show(context)
    }

    override fun onScanTreeCycleBreakSkipped() {
        Log.d(TAG, "Cycle break skipped")
        KeyboardEscapePrompt.instance.hide()
    }

    override fun onScanTreeCycleBreakSelected() {
        Log.d(TAG, "Cycle break selected")
        KeyboardEscapePrompt.instance.hide()
        handleCycleBreakSelection()
    }

    override fun onSingleCycleCompleted(cycleNumber: Int) {
        Log.d(TAG, "Cycle completed: $cycleNumber")
    }
    
    /**
     * Handles cycle break selection based on keyboard state.
     * If keyboard is visible and not escaped, escape from keyboard.
     * Otherwise, open the main menu.
     */
    private fun handleCycleBreakSelection() {
        if (KeyboardManager.shouldShowKeyboardEscapePrompt()) {
            Log.d(TAG, "Escaping from keyboard scanning")
            KeyboardManager.escapeKeyboard()
        }
    }
}
