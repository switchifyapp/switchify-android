package com.enaboapps.switchify.service.menu.menus.custom

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class MyActionsMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(accessibilityService, buildMyActionsMenuItems(accessibilityService)) {
    companion object {
        private fun buildMyActionsMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).buildMyActionsMenuObject()
                .getMenuItems()
        }
    }
}