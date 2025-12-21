package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class TapGesturesMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        buildTapGesturesMenuItems(accessibilityService),
        MenuConstants.MenuIds.TAP_GESTURES_MENU
    ) {

    companion object {
        private fun buildTapGesturesMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).tapGesturesMenuObject.getMenuItems()
        }
    }
}