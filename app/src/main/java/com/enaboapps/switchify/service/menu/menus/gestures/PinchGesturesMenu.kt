package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class PinchGesturesMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        buildPinchGesturesMenuItems(accessibilityService),
        MenuConstants.MenuIds.PINCH_GESTURES_MENU
    ) {
    companion object {
        private fun buildPinchGesturesMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).pinchGesturesMenuObject.getMenuItems()
        }
    }
}
