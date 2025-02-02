package com.enaboapps.switchify.service.methods.nodes

import android.graphics.PointF
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.selection.SelectionHandler

/**
 * This class represents a node
 */
class Node(
    private var select: (() -> Unit?)? = null
) : ScanNodeInterface {
    private var nodeInfo: AccessibilityNodeInfo? = null
    private var x: Int = 0
    private var y: Int = 0
    private var centerX: Int = 0
    private var centerY: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var highlighted: Boolean = false

    private var contentDescription: String = ""


    companion object {
        /**
         * This function creates a node from AccessibilityNodeInfo
         * @param nodeInfo The AccessibilityNodeInfo
         * @return The node
         */
        fun fromAccessibilityNodeInfo(nodeInfo: AccessibilityNodeInfo): Node {
            val node = Node()
            val rect = Rect()
            nodeInfo.getBoundsInScreen(rect)
            node.nodeInfo = nodeInfo
            node.x = rect.left
            node.y = rect.top
            node.contentDescription = nodeInfo.contentDescription?.toString() ?: ""
            node.centerX = rect.centerX()
            node.centerY = rect.centerY()
            node.width = rect.width()
            node.height = rect.height()
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
            if (menuItem.text.isNotEmpty()) {
                node.contentDescription = menuItem.text
            } else if (menuItem.drawableDescription.isNotEmpty()) {
                node.contentDescription = menuItem.drawableDescription
            }
            return node
        }
    }

    enum class ActionType {
        CUT,
        COPY,
        PASTE
    }

    fun isActionable(actionType: ActionType): Boolean {
        return when (actionType) {
            ActionType.CUT -> nodeInfo?.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CUT) == true

            ActionType.COPY -> nodeInfo?.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY) == true

            ActionType.PASTE -> nodeInfo?.actionList?.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE) == true
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

    override fun getContentDescription(): String {
        return contentDescription
    }

    fun setContentDescription(contentDescription: String) {
        this.contentDescription = contentDescription
    }

    override fun highlight() {
        NodeScannerUI.instance.showItemBounds(x, y, width, height)
        highlighted = true
    }

    override fun unhighlight() {
        NodeScannerUI.instance.hideItemBounds()
        highlighted = false
    }

    override fun select() {
        unhighlight()

        if (select == null) {
            GesturePoint.x = centerX
            GesturePoint.y = centerY

            SelectionHandler.setSelectAction {
                GestureManager.getInstance().performTap()
            }
            SelectionHandler.performSelectionAction()
        } else {
            select?.invoke()
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Node) return false

        if (x != other.x) return false
        if (y != other.y) return false
        if (centerX != other.centerX) return false
        if (centerY != other.centerY) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        result = 31 * result + centerX
        result = 31 * result + centerY
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}