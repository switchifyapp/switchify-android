package com.enaboapps.switchify.service.menu.menus.edit

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import kotlinx.coroutines.CoroutineScope

class EditMenuStructure(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    /**
     * Builds a menu structure for edit operations available at the current gesture point.
     *
     * Only includes menu items for which an actionable accessibility node is found and a corresponding
     * menu definition exists (cut, copy, paste).
     *
     * @return A `MenuStructure` with `id` set to "edit_menu", `items` containing `MenuItem` entries for
     * available edit actions, and `context` set to this instance's context.
     */
    fun buildEditMenuObject(): MenuStructure {
        val currentPoint = GesturePoint.getPoint()
        val cutNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.CUT)
        val copyNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.COPY)
        val pasteNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.PASTE)
        return MenuStructure(
            id = MenuConstants.MenuIds.EDIT_MENU,
            items = listOfNotNull(
                if (cutNode != null) {
                    MenuItemRegistry.getDefinition(MenuConstants.MenuIds.EDIT_MENU, MenuConstants.ItemIds.Edit.CUT)?.let { def ->
                        MenuItem(
                            definition = def,
                            action = {
                                cutNode.performAction(AccessibilityNodeInfo.ACTION_CUT)
                            }
                        )
                    }
                } else null,
                if (copyNode != null) {
                    MenuItemRegistry.getDefinition(MenuConstants.MenuIds.EDIT_MENU, MenuConstants.ItemIds.Edit.COPY)?.let { def ->
                        MenuItem(
                            definition = def,
                            action = {
                                copyNode.performAction(AccessibilityNodeInfo.ACTION_COPY)
                            }
                        )
                    }
                } else null,
                if (pasteNode != null) {
                    MenuItemRegistry.getDefinition(MenuConstants.MenuIds.EDIT_MENU, MenuConstants.ItemIds.Edit.PASTE)?.let { def ->
                        MenuItem(
                            definition = def,
                            action = {
                                pasteNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                            }
                        )
                    }
                } else null
            ),
            context = context,
            coroutineScope = coroutineScope
        )
    }
} 