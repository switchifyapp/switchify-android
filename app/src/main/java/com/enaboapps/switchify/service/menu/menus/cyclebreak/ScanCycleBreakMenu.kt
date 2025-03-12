package com.enaboapps.switchify.service.menu.menus.cyclebreak

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.BaseMenu

class ScanCycleBreakMenu(accessibilityService: SwitchifyAccessibilityService) : BaseMenu(
    accessibilityService = accessibilityService,
    items = ScanCycleBreakMenuStructure(accessibilityService).breakMenuObject.getMenuItems()
) 