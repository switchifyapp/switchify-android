package com.enaboapps.switchify.service.menu.menus.edit

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class EditMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(accessibilityService, buildEditMenuItems(accessibilityService)) {
    companion object {
        private fun buildEditMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).buildEditMenuObject().getMenuItems()
        }
    }
}