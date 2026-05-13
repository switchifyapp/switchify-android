package com.enaboapps.switchify.service.menu.menus.settings

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class SettingsMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        buildSettingsMenuItems(accessibilityService),
        MenuConstants.MenuIds.SETTINGS_MENU
    ) {
    companion object {
        private fun buildSettingsMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).buildSettingsMenuObject().getMenuItems()
        }
    }
}
