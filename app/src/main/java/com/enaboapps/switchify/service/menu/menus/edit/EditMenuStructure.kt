package com.enaboapps.switchify.service.menu.menus.edit

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer

class EditMenuStructure(private val context: Context) {
    fun buildEditMenuObject(): MenuStructure {
        val currentPoint = GesturePoint.getPoint()
        val cutNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.CUT)
        val copyNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.COPY)
        val pasteNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.PASTE)
        val definitions = MenuItemRegistry.getEditMenuDefinitions()
        return MenuStructure(
            id = "edit_menu",
            items = listOfNotNull(
                if (cutNode != null) {
                    definitions.find { it.id == "cut" }?.let { def ->
                        MenuItem(
                            definition = def,
                            action = {
                                cutNode.performAction(AccessibilityNodeInfo.ACTION_CUT)
                            }
                        )
                    }
                } else null,
                if (copyNode != null) {
                    definitions.find { it.id == "copy" }?.let { def ->
                        MenuItem(
                            definition = def,
                            action = {
                                copyNode.performAction(AccessibilityNodeInfo.ACTION_COPY)
                            }
                        )
                    }
                } else null,
                if (pasteNode != null) {
                    definitions.find { it.id == "paste" }?.let { def ->
                        MenuItem(
                            definition = def,
                            action = {
                                pasteNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                            }
                        )
                    }
                } else null
            ),
            context = context
        )
    }
} 