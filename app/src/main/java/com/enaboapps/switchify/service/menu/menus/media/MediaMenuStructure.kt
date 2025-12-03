package com.enaboapps.switchify.service.menu.menus.media

import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class MediaMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {
    private val openVolumeControlMenu = MenuItemRegistry.getMediaControlMenuDefinitions()
        .find { it.id == "volume_control" }?.let { def ->
            MenuItem(
                definition = def,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openVolumeControlMenu() }
            )
        }!!

    val mediaControlMenuObject = MenuStructure(
        id = "media_control_menu",
        items = listOfNotNull(
            MenuItemRegistry.getMediaControlMenuDefinitions().find { it.id == "play_pause" }?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GlobalActionManager.toggleMediaPlayback() }
                )
            },
            openVolumeControlMenu
        ),
        context = accessibilityService
    )
} 