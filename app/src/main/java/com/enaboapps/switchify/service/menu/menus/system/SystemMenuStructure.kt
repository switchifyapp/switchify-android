package com.enaboapps.switchify.service.menu.menus.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.window.ServiceMessageHUD

class SystemMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {
    val systemNavItems = listOf(
        MenuItem(
            id = "sys_back",
            drawableId = R.drawable.ic_sys_back,
            drawableDescription = "Back",
            action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        ),
        MenuItem(
            id = "sys_home",
            drawableId = R.drawable.ic_sys_home,
            drawableDescription = "Home",
            action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
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
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Recent Apps is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "notifications",
                    text = "Notifications",
                    action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
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
                    action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) }
                ),
                MenuItem(
                    id = "lock_screen",
                    text = "Lock Screen",
                    action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
                ),
                MenuItem(
                    id = "power_dialog",
                    text = "Power Dialog",
                    action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) }
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
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.adjustStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    AudioManager.ADJUST_RAISE,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "volume_down",
                    text = "Volume Down",
                    closeOnSelect = false,
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.adjustStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    AudioManager.ADJUST_LOWER,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "full_volume",
                    text = "Full Volume",
                    closeOnSelect = false,
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY),
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "mute",
                    text = "Mute",
                    closeOnSelect = false,
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    0,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "half_volume",
                    text = "Half Volume",
                    closeOnSelect = false,
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val halfVolume =
                                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY) / 2
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    halfVolume,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                )
            )
        )
    }
} 