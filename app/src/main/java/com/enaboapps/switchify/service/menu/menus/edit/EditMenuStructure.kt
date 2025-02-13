package com.enaboapps.switchify.service.menu.menus.edit

import android.view.accessibility.AccessibilityNodeInfo
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.methods.nodes.Node
import com.enaboapps.switchify.service.methods.nodes.NodeExaminer

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
                        text = "Cut",
                        action = {
                            cutNode.performAction(AccessibilityNodeInfo.ACTION_CUT)
                        }
                    )
                } else null,
                if (copyNode != null) {
                    MenuItem(
                        id = "copy",
                        text = "Copy",
                        action = {
                            copyNode.performAction(AccessibilityNodeInfo.ACTION_COPY)
                        }
                    )
                } else null,
                if (pasteNode != null) {
                    MenuItem(
                        id = "paste",
                        text = "Paste",
                        action = {
                            pasteNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        }
                    )
                } else null
            )
        )
    }
} 