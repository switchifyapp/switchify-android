package com.enaboapps.switchify.service.menu.menus.media

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class MediaMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {
    private val openVolumeControlMenu = MenuItem(
        id = "volume_control",
        textResource = R.string.action_volume_control,
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openVolumeControlMenu() }
    )

    val mediaControlMenuObject = MenuStructure(
        id = "media_control_menu",
        items = listOf(
            MenuItem(
                id = "play_pause",
                textResource = R.string.menu_item_play_pause,
                action = { GlobalActionManager.toggleMediaPlayback() }
            ),
            openVolumeControlMenu
        )
    )
} 