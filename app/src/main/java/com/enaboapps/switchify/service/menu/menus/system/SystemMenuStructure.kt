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
            drawableDescription = "Back",
            action = { GlobalActionManager.goBack() }
        ),
        MenuItem(
            id = "sys_home",
            drawableId = R.drawable.ic_sys_home,
            drawableDescription = "Home",
            action = { GlobalActionManager.goHome() }
        )
    )

    private val openVolumeControlMenu = MenuItem(
        id = "volume_control",
        text = "Volume Control",
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openVolumeControlMenu() }
    )

    fun buildDeviceMenuObject(): MenuStructure {
        return MenuStructure(
            id = "device_menu",
            items = listOfNotNull(
                MenuItem(
                    id = "recent_apps",
                    text = "Recent Apps",
                    action = { GlobalActionManager.openRecents() }
                ),
                MenuItem(
                    id = "notifications",
                    text = "Notifications",
                    action = { GlobalActionManager.openNotifications() }
                ),
                MenuItem(
                    id = "open_assistant",
                    text = "Open Assistant",
                    action = {
                        val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        accessibilityService?.startActivity(intent)
                    }
                ),
                MenuItem(
                    id = "quick_settings",
                    text = "Quick Settings",
                    action = { GlobalActionManager.openQuickSettings() }
                ),
                MenuItem(
                    id = "lock_screen",
                    text = "Lock Screen",
                    action = { GlobalActionManager.lockScreen() }
                ),
                MenuItem(
                    id = "power_dialog",
                    text = "Power Dialog",
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
                    text = "Volume Up",
                    closeOnSelect = false,
                    action = { AudioActionManager.volumeUp() }
                ),
                MenuItem(
                    id = "volume_down",
                    text = "Volume Down",
                    closeOnSelect = false,
                    action = { AudioActionManager.volumeDown() }
                ),
                MenuItem(
                    id = "full_volume",
                    text = "Full Volume",
                    closeOnSelect = false,
                    action = { AudioActionManager.setFullVolume() }
                ),
                MenuItem(
                    id = "mute",
                    text = "Mute",
                    closeOnSelect = false,
                    action = { AudioActionManager.mute() }
                ),
                MenuItem(
                    id = "half_volume",
                    text = "Half Volume",
                    closeOnSelect = false,
                    action = { AudioActionManager.setHalfVolume() }
                )
            )
        )
    }
} 