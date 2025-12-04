package com.enaboapps.switchify.service.menu.menus.media

import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import kotlinx.coroutines.CoroutineScope

class MediaMenuStructure(
    private val accessibilityService: SwitchifyAccessibilityService?,
    private val coroutineScope: CoroutineScope
) {
    private val openVolumeControlMenu: MenuItem? =
        MenuItemRegistry.getDefinition(MenuConstants.MenuIds.MEDIA_CONTROL_MENU, MenuConstants.ItemIds.Media.VOLUME_CONTROL)?.let { def ->
            MenuItem(
                definition = def,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openVolumeControlMenu() }
            )
        }

    val mediaControlMenuObject = MenuStructure(
        id = MenuConstants.MenuIds.MEDIA_CONTROL_MENU,
        items = listOfNotNull(
            MenuItemRegistry.getDefinition(MenuConstants.MenuIds.MEDIA_CONTROL_MENU, MenuConstants.ItemIds.Media.PLAY_PAUSE)?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GlobalActionManager.toggleMediaPlayback() }
                )
            },
            openVolumeControlMenu
        ),
        context = accessibilityService,
        coroutineScope = coroutineScope
    )
} 