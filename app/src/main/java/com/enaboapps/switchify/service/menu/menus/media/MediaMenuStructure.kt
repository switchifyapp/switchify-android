package com.enaboapps.switchify.service.menu.menus.media

import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.core.GlobalActionManager
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
                action = { GlobalActionManager.toggleMediaPlayback() }
            ),
            openVolumeControlMenu
        )
    )
} 