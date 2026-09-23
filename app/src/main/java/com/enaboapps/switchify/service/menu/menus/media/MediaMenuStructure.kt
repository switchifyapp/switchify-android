package com.enaboapps.switchify.service.menu.menus.media

import com.enaboapps.switchify.service.actions.AudioActionManager
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.actions.MediaPlaybackState
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
                val active = AudioActionManager.playbackState() == MediaPlaybackState.ACTIVE
                MenuItem(
                    id = def.id,
                    labelResource = if (active) R.string.menu_item_media_pause else R.string.menu_item_media_play,
                    descriptionResource = def.descriptionResource,
                    drawableId = if (active) R.drawable.ic_pause else R.drawable.ic_play,
                    action = { AudioActionManager.togglePlayback() }
                )
            },
            MenuItemRegistry.getDefinition(MenuConstants.MenuIds.MEDIA_CONTROL_MENU, MenuConstants.ItemIds.Media.PREVIOUS_TRACK)?.let { def ->
                MenuItem(
                    definition = def,
                    closeOnSelect = false,
                    action = { AudioActionManager.previousTrack() }
                )
            },
            MenuItemRegistry.getDefinition(MenuConstants.MenuIds.MEDIA_CONTROL_MENU, MenuConstants.ItemIds.Media.NEXT_TRACK)?.let { def ->
                MenuItem(
                    definition = def,
                    closeOnSelect = false,
                    action = { AudioActionManager.nextTrack() }
                )
            },
            openVolumeControlMenu
        ),
        context = accessibilityService,
        coroutineScope = coroutineScope
    )
}
