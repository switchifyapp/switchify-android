package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.menu.KeyboardEscapePrompt
import com.enaboapps.switchify.service.scanning.CycleBreakListener
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.scanning.tree.ScanTreeCallback
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun <T> refreshScannerConfiguration(
    nodes: List<T>?,
    isAutoScanning: () -> Boolean,
    rebuild: (List<T>) -> Unit,
    resumeAutoScanning: () -> Unit
): Boolean {
    val currentNodes = nodes ?: return false
    val shouldResume = isAutoScanning()
    rebuild(currentNodes)
    if (shouldResume) {
        resumeAutoScanning()
    }
    return true
}

/**
 * Base class for node scanners that provides common functionality for both system and keyboard scanners.
 *
 * @param cycleBreakListener Optional listener to be notified when cycle break is selected.
 *                           This decouples the scanner from keyboard management logic.
 */
abstract class BaseNodeScanner(
    private val cycleBreakListener: CycleBreakListener? = null
) : ScanTreeCallback {
    protected lateinit var context: Context
    private var _scanTree: ScanTree? = null
    val scanTree: ScanTree
        get() {
            val existing = _scanTree
            if (existing != null) return existing
            check(::context.isInitialized) { "BaseNodeScanner.start(context) must be called before accessing scanTree" }
            val created = ScanTree(
                context = context,
                stopScanningOnSelect = true,
                hasCycleBreak = { KeyboardManager.shouldEnableCycleBreak() },
                visualEffectsEnabled = true,
                callback = this
            )
            _scanTree = created
            return created
        }
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var revertToCursorJob: Job? = null

    private var source: String? = null

    private var lastUpdateNodes: List<Node>? = null

    companion object {
        private const val TAG = "BaseNodeScanner"
        private const val EMPTY_NODES_TIMEOUT_MS = 5000L
    }

    open fun start(context: Context) {
        this.context = context
        startTimeoutToRevertToCursor()
        scanTree
    }

    open fun updateNodes(nodes: List<Node>) {
        updateSnapshot(nodes, nodes.firstOrNull()?.scanIdentity?.source ?: source)
    }

    internal fun updateSnapshot(nodes: List<Node>, nextSource: String?) {
        val resolvedSource = nextSource ?: nodes.firstOrNull()?.scanIdentity?.source ?: source
        val sourceChanged = source != null && resolvedSource != null && source != resolvedSource
        source = resolvedSource
        _scanTree?.reconcileNodes(nodes, sourceChanged)
        lastUpdateNodes = nodes
        if (nodes.isEmpty()) {
            if (revertToCursorJob?.isActive != true) startTimeoutToRevertToCursor()
        } else stopTimeoutToRevertToCursor()
    }

    protected fun buildInitialNodes(nodes: List<Node>) {
        source = nodes.firstOrNull()?.scanIdentity?.source
        buildFromNodes(nodes)
        lastUpdateNodes = nodes
    }

    internal fun refreshConfiguration(): Boolean = refreshScannerConfiguration(
        nodes = lastUpdateNodes,
        isAutoScanning = scanTree::isAutoScanning,
        rebuild = {
            scanTree.reloadSpeed()
            buildFromNodes(it)
        },
        resumeAutoScanning = scanTree::startAutoScanning
    )

    internal fun refreshTiming() {
        scanTree.reloadSpeed()
    }
    open fun cleanup() {
        revertToCursorJob?.cancel()
        source = null
        _scanTree?.cleanup()
        _scanTree = null
        lastUpdateNodes = null
    }

    open fun startTimeoutToRevertToCursor() {
        revertToCursorJob?.cancel()
        revertToCursorJob = coroutineScope.launch {
            delay(EMPTY_NODES_TIMEOUT_MS)
            if (_scanTree != null && scanTree.isEmpty()) {
                switchToCursorMode("empty nodes")
            }
        }
    }

    private fun stopTimeoutToRevertToCursor() {
        revertToCursorJob?.cancel()
        revertToCursorJob = null
    }

    protected open fun buildFromNodes(nodes: List<Node>) {
        _scanTree?.buildTree(nodes)
    }

    private fun switchToCursorMode(reason: String) {
        coroutineScope.launch(Dispatchers.Main) {
            _scanTree?.stopScanningAndReset()
            if (AccessTechnique.getCurrentTechnique() == AccessTechnique.Technique.ITEM_SCAN) {
                val manager = com.enaboapps.switchify.service.core.ServiceCore.getScanningManager()
                val switched = manager?.enterEmptyNodesFallback() ?: run {
                    AccessTechnique.setTemporaryTechnique(AccessTechnique.Technique.POINT_SCAN)
                    true
                }
                if (switched) Log.d(TAG, "Temporarily switched to point scan due to $reason")
            }
        }
    }

    // ScanTreeCallback implementation
    override fun onScanTreeCycleBreakStarted() {
        Log.d(TAG, "Cycle break started")
        val keyboardTarget = KeyboardManager.keyboardState.value.keyboardWindowTarget
        val displayTarget = keyboardTarget?.let { target ->
            OverlayTarget.Display(
                displayId = target.displayId,
                forceSurface = target.displayId != OverlayTargets.DEFAULT_DISPLAY_ID
            )
        } ?: OverlayTargets.defaultDisplay()
        KeyboardEscapePrompt.instance.show(context, displayTarget)
    }

    override fun onScanTreeCycleBreakSkipped() {
        Log.d(TAG, "Cycle break skipped")
        KeyboardEscapePrompt.instance.hide()
    }

    override fun onScanTreeCycleBreakSelected() {
        Log.d(TAG, "Cycle break selected")
        KeyboardEscapePrompt.instance.hide()

        // Notify listener instead of directly handling keyboard logic
        cycleBreakListener?.onCycleBreak()
    }

    override fun onSingleCycleCompleted(cycleNumber: Int) {
        Log.d(TAG, "Cycle completed: $cycleNumber")
    }
}
