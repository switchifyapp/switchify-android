package com.enaboapps.switchify.service.menu.menus.cyclebreak

import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.system.SystemMenuStructure

class ScanCycleBreakMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {
    val breakMenuObject =
        SystemMenuStructure(accessibilityService).buildDeviceMenuObject()
} 