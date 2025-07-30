package com.enaboapps.switchify.service.menu.menus.system

import android.content.Intent
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.actions.AudioActionManager
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.screenshot.ScreenshotManager

class SystemMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {

    private val openVolumeControlMenu = MenuItem(
        id = "volume_control",
        labelResource = R.string.action_volume_control,
        drawableId = R.drawable.ic_volume_control,
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openVolumeControlMenu() }
    )

    fun buildDeviceMenuObject(): MenuStructure {
        return MenuStructure(
            id = "device_menu",
            items = listOfNotNull(
                MenuItem(
                    id = "recent_apps",
                    labelResource = R.string.system_recents,
                    drawableId = R.drawable.ic_recent_apps,
                    action = { GlobalActionManager.openRecents() }
                ),
                MenuItem(
                    id = "notifications",
                    labelResource = R.string.system_notifications,
                    drawableId = R.drawable.ic_notifications,
                    action = { GlobalActionManager.openNotifications() }
                ),
                MenuItem(
                    id = "open_assistant",
                    labelResource = R.string.system_assistant,
                    drawableId = R.drawable.ic_assistant,
                    action = {
                        val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        accessibilityService?.startActivity(intent)
                    }
                ),
                MenuItem(
                    id = "quick_settings",
                    labelResource = R.string.system_quick_settings,
                    drawableId = R.drawable.ic_quick_settings,
                    action = { GlobalActionManager.openQuickSettings() }
                ),
                MenuItem(
                    id = "lock_screen",
                    labelResource = R.string.system_lock_screen,
                    drawableId = R.drawable.ic_lock_screen,
                    action = { GlobalActionManager.lockScreen() }
                ),
                MenuItem(
                    id = "power_dialog",
                    labelResource = R.string.system_power_dialog,
                    drawableId = R.drawable.ic_power_dialog,
                    action = { GlobalActionManager.openPowerDialog() }
                ),
                MenuItem(
                    id = "take_screenshot",
                    labelResource = R.string.system_screenshot,
                    drawableId = R.drawable.ic_screenshot,
                    action = {
                        accessibilityService?.let { service ->
                            ScreenshotManager.takeScreenshotAndSave(
                                accessibilityService = service,
                                context = service.applicationContext
                            )
                        }
                    }
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
                    labelResource = R.string.menu_item_volume_up,
                    drawableId = R.drawable.ic_volume_up,
                    closeOnSelect = false,
                    action = { AudioActionManager.volumeUp() }
                ),
                MenuItem(
                    id = "volume_down",
                    labelResource = R.string.menu_item_volume_down,
                    drawableId = R.drawable.ic_volume_down,
                    closeOnSelect = false,
                    action = { AudioActionManager.volumeDown() }
                ),
                MenuItem(
                    id = "full_volume",
                    labelResource = R.string.menu_item_full_volume,
                    drawableId = R.drawable.ic_full_volume,
                    closeOnSelect = false,
                    action = { AudioActionManager.setFullVolume() }
                ),
                MenuItem(
                    id = "mute",
                    labelResource = R.string.menu_item_mute,
                    drawableId = R.drawable.ic_mute,
                    closeOnSelect = false,
                    action = { AudioActionManager.mute() }
                ),
                MenuItem(
                    id = "half_volume",
                    labelResource = R.string.menu_item_half_volume,
                    drawableId = R.drawable.ic_half_volume,
                    closeOnSelect = false,
                    action = { AudioActionManager.setHalfVolume() }
                )
            )
        )
    }
} 