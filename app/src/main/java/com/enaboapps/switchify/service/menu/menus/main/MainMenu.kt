package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class MainMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(accessibilityService, buildMainMenuItems(accessibilityService)) {

    companion object {
        private fun buildMainMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).buildMainMenuObject().getMenuItems()
        }
    }
}