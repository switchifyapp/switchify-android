package com.enaboapps.switchify.service.menu.menus.actions

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.techniques.nodes.NodeActionTarget

internal class AccessibilityActionsMenu(
    accessibilityService: SwitchifyAccessibilityService,
    target: NodeActionTarget
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = buildItems(target),
    menuId = MenuConstants.MenuIds.ACCESSIBILITY_ACTIONS_MENU
) {
    private companion object {
        fun buildItems(target: NodeActionTarget): List<MenuItem> {
            return target.actions.map { action ->
                MenuItem(
                    id = "accessibility_action_${action.id}",
                    userProvidedText = action.label,
                    descriptionResource = R.string.menu_item_accessibility_action_description,
                    closeOnSelect = false,
                    action = {
                        MenuManager.getInstance().selectAccessibilityAction(target, action.id)
                    }
                )
            }
        }
    }
}
