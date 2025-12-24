package com.enaboapps.switchify.service.menu.menus.system

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class VolumeControlMenu(private val accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        MenuStructureHolder(accessibilityService).buildVolumeControlMenuObject().getMenuItems(),
        MenuConstants.MenuIds.VOLUME_CONTROL_MENU
    )
