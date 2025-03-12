package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class GesturesMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(accessibilityService, buildGesturesMenuItems(accessibilityService)) {

    companion object {
        private fun buildGesturesMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).gesturesMenuObject.getMenuItems()
        }
    }
}