package com.enaboapps.switchify.service.menu.menus.system

import android.content.Intent
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.core.AudioActionManager
import com.enaboapps.switchify.service.core.GlobalActionManager
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class SystemMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {
    val systemNavItems = listOf(
        MenuItem(
            id = "sys_back",
            drawableId = R.drawable.ic_sys_back,
            drawableDescriptionResource = R.string.system_back,
            action = { GlobalActionManager.goBack() }
        ),
        MenuItem(
            id = "sys_home",
            drawableId = R.drawable.ic_sys_home,
            drawableDescriptionResource = R.string.system_home,
            action = { GlobalActionManager.goHome() }
        )
    )

    private val openVolumeControlMenu = MenuItem(
        id = "volume_control",
        textResource = R.string.action_volume_control,
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openVolumeControlMenu() }
    )

    fun buildDeviceMenuObject(): MenuStructure {
        return MenuStructure(
            id = "device_menu",
            items = listOfNotNull(
                MenuItem(
                    id = "recent_apps",
                    textResource = R.string.system_recents,
                    action = { GlobalActionManager.openRecents() }
                ),
                MenuItem(
                    id = "notifications",
                    textResource = R.string.system_notifications,
                    action = { GlobalActionManager.openNotifications() }
                ),
                MenuItem(
                    id = "open_assistant",
                    textResource = R.string.system_assistant,
                    action = {
                        val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        accessibilityService?.startActivity(intent)
                    }
                ),
                MenuItem(
                    id = "quick_settings",
                    textResource = R.string.system_quick_settings,
                    action = { GlobalActionManager.openQuickSettings() }
                ),
                MenuItem(
                    id = "lock_screen",
                    textResource = R.string.system_lock_screen,
                    action = { GlobalActionManager.lockScreen() }
                ),
                MenuItem(
                    id = "power_dialog",
                    textResource = R.string.system_power_dialog,
                    action = { GlobalActionManager.openPowerDialog() }
                ),
                openVolumeControlMenu
            )
        )
    }

    fun buildVolumeControlMenuObject(): MenuStructure {
        return MenuStructure(
            id = "volume_control_menu",
            items = listOf(
                MenuItem(
                    id = "volume_up",
                    textResource = R.string.menu_item_volume_up,
                    closeOnSelect = false,
                    action = { AudioActionManager.volumeUp() }
                ),
                MenuItem(
                    id = "volume_down",
                    textResource = R.string.menu_item_volume_down,
                    closeOnSelect = false,
                    action = { AudioActionManager.volumeDown() }
                ),
                MenuItem(
                    id = "full_volume",
                    textResource = R.string.menu_item_full_volume,
                    closeOnSelect = false,
                    action = { AudioActionManager.setFullVolume() }
                ),
                MenuItem(
                    id = "mute",
                    textResource = R.string.menu_item_mute,
                    closeOnSelect = false,
                    action = { AudioActionManager.mute() }
                ),
                MenuItem(
                    id = "half_volume",
                    textResource = R.string.menu_item_half_volume,
                    closeOnSelect = false,
                    action = { AudioActionManager.setHalfVolume() }
                )
            )
        )
    }
} 