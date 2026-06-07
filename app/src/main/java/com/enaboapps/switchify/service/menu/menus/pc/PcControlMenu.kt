package com.enaboapps.switchify.service.menu.menus.pc

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuLayoutMode
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class PcControlMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        buildPcControlItems(accessibilityService),
        MenuConstants.MenuIds.PC_CONTROL_MENU,
        layoutMode = MenuLayoutMode.Grid(columns = 3)
    ) {
    companion object {
        private fun buildPcControlItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).pcControlMenuObject.getMenuItems()
        }
    }
}
