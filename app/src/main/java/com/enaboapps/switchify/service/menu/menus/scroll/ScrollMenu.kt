package com.enaboapps.switchify.service.menu.menus.scroll

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class ScrollMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        buildScrollMenuItems(accessibilityService),
        MenuConstants.MenuIds.SCROLL_MENU
    ) {
    companion object {
        private fun buildScrollMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).scrollMenuObject.getMenuItems()
        }
    }
}