package com.enaboapps.switchify.service.menu.menus.edit

import android.view.accessibility.AccessibilityNodeInfo
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer

class EditMenuStructure {
    fun buildEditMenuObject(): MenuStructure {
        val currentPoint = GesturePoint.getPoint()
        val cutNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.CUT)
        val copyNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.COPY)
        val pasteNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.PASTE)
        return MenuStructure(
            id = "edit_menu",
            items = listOfNotNull(
                if (cutNode != null) {
                    MenuItem(
                        id = "cut",
                        labelResource = R.string.menu_item_cut,
                        drawableId = R.drawable.ic_cut,
                        action = {
                            cutNode.performAction(AccessibilityNodeInfo.ACTION_CUT)
                        }
                    )
                } else null,
                if (copyNode != null) {
                    MenuItem(
                        id = "copy",
                        labelResource = R.string.menu_item_copy,
                        drawableId = R.drawable.ic_copy,
                        action = {
                            copyNode.performAction(AccessibilityNodeInfo.ACTION_COPY)
                        }
                    )
                } else null,
                if (pasteNode != null) {
                    MenuItem(
                        id = "paste",
                        labelResource = R.string.menu_item_paste,
                        drawableId = R.drawable.ic_paste,
                        action = {
                            pasteNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        }
                    )
                } else null
            )
        )
    }
} 