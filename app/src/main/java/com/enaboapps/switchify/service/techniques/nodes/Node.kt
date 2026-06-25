package com.enaboapps.switchify.service.techniques.nodes

import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.nodes.scanners.NodeScannerUI
import com.enaboapps.switchify.service.techniques.pointscan.blocks.PointScanBlock
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import com.enaboapps.switchify.utils.Resources

data class NodeScanSignature(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val contentDescription: String,
    val description: String,
    val elementType: String?
)

/**
 * This class represents a node
 */
class Node(
    private var onSelect: (() -> Unit?)? = null
) : ScanNodeInterface {
    private var nodeInfo: AccessibilityNodeInfo? = null
    private var x: Int = 0
    private var y: Int = 0
    private var centerX: Int = 0
    private var centerY: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var highlighted: Boolean = false
    private var overlayNodeBounds: OverlayNodeBounds? = null
    private var capabilities: NodeCapabilities? = null

    private var contentDescription: String = ""

    private var description: String = ""

    /**
     * Optional hook fired after this node is highlighted during scanning.
     * Stays null for scan nodes that don't need to notify anyone; the menu
     * system uses it to surface the currently-focused ring item's full label.
     */
    var onHighlight: ((Node) -> Unit)? = null

    /**
     * Companion hook to [onHighlight]; fires after unhighlight. Typically used
     * to clear whatever state [onHighlight] set.
     */
    var onUnhighlight: (() -> Unit)? = null


    companion object {
        private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

        /**
         * This function creates a node from AccessibilityNodeInfo
         * @param nodeInfo The AccessibilityNodeInfo
         * @return The node
         */
        fun fromAccessibilityNodeInfo(nodeInfo: AccessibilityNodeInfo): Node {
            val node = Node()
            val rect = Rect()
            nodeInfo.getBoundsInScreen(rect)
            val boundsInWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Rect().also { nodeInfo.getBoundsInWindow(it) }
            } else {
                null
            }
            val overlayBounds = overlayBoundsFor(nodeInfo, rect, boundsInWindow)
            node.nodeInfo = nodeInfo
            node.x = rect.left
            node.y = rect.top
            node.contentDescription = nodeInfo.contentDescription?.toString() ?: ""
            node.centerX = rect.centerX()
            node.centerY = rect.centerY()
            node.width = rect.width()
            node.height = rect.height()
            node.overlayNodeBounds = overlayBounds
            node.capabilities = NodeCapabilityClassifier.classify(nodeInfo, rect, boundsInWindow)
            return node
        }

        /**
         * This function creates a node from a MenuItem object
         * @param menuItem The MenuItem object
         * @return The node
         */
        fun fromMenuItem(menuItem: MenuItem): Node {
            val node = Node { menuItem.select() }
            node.x = menuItem.x
            node.y = menuItem.y
            node.centerX = menuItem.x + menuItem.width / 2
            node.centerY = menuItem.y + menuItem.height / 2
            node.width = menuItem.width
            node.height = menuItem.height
            node.overlayNodeBounds = OverlayNodeBounds.displayOnly(
                Rect(node.x, node.y, node.x + node.width, node.y + node.height),
                forceSurface = true
            )
            val text = if (menuItem.labelResource != null) {
                Resources.getString(menuItem.labelResource)
            } else {
                menuItem.userProvidedText
            }
            if (text != null) {
                node.contentDescription = text
            }
            val descText = menuItem.descriptionResource?.let { Resources.getString(it) }
                ?: menuItem.userProvidedDescription
            if (descText != null) {
                node.description = descText
            }
            return node
        }

        /**
         * This function creates a node from a PointScanBlock
         * @param cursorBlock The PointScanBlock to create the node from
         * @return The node
         */
        fun fromPointScanBlock(cursorBlock: PointScanBlock): Node {
            val node = Node()
            node.x = cursorBlock.left
            node.y = cursorBlock.top
            node.width = cursorBlock.width
            node.height = cursorBlock.height
            node.centerX = cursorBlock.left + (cursorBlock.width / 2)
            node.centerY = cursorBlock.top + (cursorBlock.height / 2)
            node.overlayNodeBounds = OverlayNodeBounds.displayOnly(
                Rect(node.x, node.y, node.x + node.width, node.y + node.height)
            )
            node.contentDescription = "Block ${cursorBlock.position + 1}"
            return node
        }

        private fun overlayBoundsFor(
            nodeInfo: AccessibilityNodeInfo,
            boundsInScreen: Rect,
            boundsInWindow: Rect?
        ): OverlayNodeBounds {
            val window = nodeInfo.window
            val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.displayId ?: OverlayTargets.DEFAULT_DISPLAY_ID
            } else {
                OverlayTargets.DEFAULT_DISPLAY_ID
            }
            return OverlayNodeBounds(
                displayId = displayId,
                windowId = nodeInfo.windowId.takeIf { it >= 0 },
                windowType = window?.type,
                boundsInScreen = Rect(boundsInScreen),
                boundsInWindow = boundsInWindow
            )
        }
    }

    enum class ActionType {
        CUT,
        COPY,
        PASTE
    }

    fun isActionable(actionType: ActionType): Boolean {
        val caps = capabilities
        return when (actionType) {
            ActionType.CUT -> caps?.hasCutAction
                ?: (nodeInfo?.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CUT) == true)

            ActionType.COPY -> caps?.hasCopyAction
                ?: (nodeInfo?.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY) == true)

            ActionType.PASTE -> caps?.hasPasteAction
                ?: (nodeInfo?.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE) == true)
        }
    }

    /**
     * This function performs an action on the node
     *
     * @param action The action to perform
     */
    fun performAction(action: Int) {
        nodeInfo?.performAction(action)
    }

    /**
     * This function returns whether the node contains a point
     *
     * @param point The point to check
     * @return True if the node contains the point, false otherwise
     */
    fun containsPoint(point: PointF): Boolean {
        return point.x >= x && point.x <= x + width && point.y >= y && point.y <= y + height
    }

    override fun getMidX(): Int {
        return centerX
    }

    override fun getMidY(): Int {
        return centerY
    }

    override fun getLeft(): Int {
        return x
    }

    override fun getTop(): Int {
        return y
    }

    override fun getWidth(): Int {
        return width
    }

    override fun getHeight(): Int {
        return height
    }

    fun getBounds(): Rect {
        return Rect(x, y, x + width, y + height)
    }

    fun getOverlayTargetForHighlight(): OverlayTarget {
        val overlayBounds = overlayNodeBounds ?: return OverlayTargets.defaultDisplay()
        return targetForOverlayBounds(overlayBounds)
    }

    fun getOverlayHighlightBounds(target: OverlayTarget): Rect {
        return overlayNodeBounds?.highlightBounds(target) ?: getBounds()
    }

    internal fun getCapabilities(): NodeCapabilities? = capabilities

    internal fun isCurrentlyScannable(): Boolean {
        return capabilities?.isCurrentlyScannable ?: false
    }

    internal fun prefersAccessibilityClickForSelection(): Boolean {
        return capabilities?.prefersAccessibilityClickForSelection ?: true
    }

    override fun getContentDescription(): String {
        return contentDescription
    }

    fun setContentDescription(contentDescription: String) {
        this.contentDescription = contentDescription
    }

    fun getDescription(): String {
        return description
    }

    override fun highlight() {
        NodeCapabilityDebugLogger.log("highlight", this)
        val overlayBounds = overlayNodeBounds
        if (overlayBounds != null) {
            val target = targetForOverlayBounds(overlayBounds)
            val bounds = overlayBounds.highlightBounds(target)
            NodeScannerUI.Companion.instance.showItemBounds(
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height(),
                target
            )
        } else {
            NodeScannerUI.Companion.instance.showItemBounds(x, y, width, height)
        }
        highlighted = true
        onHighlight?.invoke(this)
    }

    override fun unhighlight() {
        NodeScannerUI.Companion.instance.hideItemBounds()
        highlighted = false
        onUnhighlight?.invoke()
    }

    private fun targetForOverlayBounds(overlayBounds: OverlayNodeBounds): OverlayTarget {
        val preferredTarget = overlayBounds.target()
        return when {
            overlayBounds.isInputMethodWindow() -> {
                OverlayTarget.Display(
                    displayId = overlayBounds.displayId,
                    forceSurface = overlayBounds.displayId != OverlayTargets.DEFAULT_DISPLAY_ID
                )
            }

            preferredTarget is OverlayTarget.Window &&
                    SwitchifyAccessibilityWindow.instance.canAttachSurfaceOverlay(preferredTarget) -> {
                preferredTarget
            }

            else -> {
                OverlayTargets.displayFallback(preferredTarget)
            }
        }
    }

    override fun select() {
        unhighlight()

        if (onSelect == null) {
            NodeCapabilityDebugLogger.log("select", this)
            GesturePoint.x = centerX
            GesturePoint.y = centerY

            SelectionHandler.setSelectAction {
                NodeSelectionPerformer.perform(
                    nodeInfo = nodeInfo,
                    preferAccessibilityClick = prefersAccessibilityClickForSelection()
                ) {
                    GestureManager.instance.performTap(overrideFingerMode = com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE)
                }
            }
            SelectionHandler.performSelectionAction()
        } else {
            mainHandler.post {
                onSelect?.invoke()
            }
        }
    }

    fun setOnSelect(onSelect: () -> Unit) {
        this.onSelect = onSelect
    }

    /**
     * Returns the class name of the accessibility node (e.g., "android.widget.Button")
     * @return The class name string, or null if not available
     */
    fun getClassName(): String? {
        return nodeInfo?.className?.toString()
    }

    /**
     * Returns the simplified element type (e.g., "Button" instead of "android.widget.Button")
     * @return The simplified element type, or null if not available
     */
    fun getElementType(): String? {
        return getClassName()?.substringAfterLast('.')
    }

    fun scanSignature(): NodeScanSignature {
        return NodeScanSignature(
            left = getLeft(),
            top = getTop(),
            width = getWidth(),
            height = getHeight(),
            contentDescription = getContentDescription(),
            description = getDescription(),
            elementType = getElementType()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Node) return false

        return x == other.x &&
                y == other.y &&
                width == other.width &&
                height == other.height &&
                contentDescription == other.contentDescription &&
                description == other.description &&
                getElementType() == other.getElementType()
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + contentDescription.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + (getElementType()?.hashCode() ?: 0)
        return result
    }
}
