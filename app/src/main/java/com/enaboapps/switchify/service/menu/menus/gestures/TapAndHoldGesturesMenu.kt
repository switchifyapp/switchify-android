package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class TapAndHoldGesturesMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        buildTapAndHoldGesturesMenuItems(accessibilityService),
        MenuConstants.MenuIds.TAP_AND_HOLD_MENU
    ) {

    companion object {
        private fun buildTapAndHoldGesturesMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).tapAndHoldGesturesMenuObject.getMenuItems()
        }
    }
}
