package com.enaboapps.switchify.service.menu.menus.media

import android.accessibilityservice.AccessibilityService
import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class MediaMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {
    private val openVolumeControlMenu = MenuItem(
        id = "volume_control",
        text = "Volume Control",
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openVolumeControlMenu() }
    )

    val mediaControlMenuObject = MenuStructure(
        id = "media_control_menu",
        items = listOf(
            MenuItem(
                id = "play_pause",
                text = "Play/Pause",
                action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK) }
            ),
            openVolumeControlMenu
        )
    )
} 