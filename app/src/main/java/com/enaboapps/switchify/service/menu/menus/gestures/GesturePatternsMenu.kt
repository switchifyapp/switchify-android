package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu

class GesturePatternsMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(accessibilityService, buildGesturePatternsMenuItems(accessibilityService)) {

    companion object {
        private fun buildGesturePatternsMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return GestureMenuStructure(accessibilityService).gesturePatternsMenuObject.getMenuItems()
        }
    }
}