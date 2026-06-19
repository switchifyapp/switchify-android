package com.enaboapps.switchify.service.menu.menus.settings

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import kotlinx.coroutines.CoroutineScope

class SettingsMenuStructure(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val coroutineScope: CoroutineScope
) {
    private val scanSettings = ScanSettings(accessibilityService)

    /**
     * Builds the Settings submenu structure based on current runtime state.
     *
     * Surfaces technique-switch items only when the user is not already on that
     * technique (and, for radar/point scan, not in directional scan mode). The
     * head-control toggle appears only when camera permission has been granted.
     */
    fun buildSettingsMenuObject(): MenuStructure {
        // While the menu is open the current technique is MENU, so resolve the
        // user's underlying technique from preferences to gate which switches
        // to show.
        val storedTechnique = AccessTechnique.getStoredTechnique()
        return MenuStructure(
        id = MenuConstants.MenuIds.SETTINGS_MENU,
        items = listOfNotNull(
            if (storedTechnique != AccessTechnique.Technique.ITEM_SCAN) {
                MenuItemRegistry.getDefinition(
                    MenuConstants.MenuIds.SETTINGS_MENU,
                    MenuConstants.ItemIds.Settings.SWITCH_TO_ITEM_SCAN
                )?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { MenuManager.getInstance().switchToItemScan() }
                    )
                }
            } else null,
            if (storedTechnique != AccessTechnique.Technique.RADAR &&
                !scanSettings.isDirectionalScanMode()
            ) {
                MenuItemRegistry.getDefinition(
                    MenuConstants.MenuIds.SETTINGS_MENU,
                    MenuConstants.ItemIds.Settings.SWITCH_TO_RADAR
                )?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { MenuManager.getInstance().switchToRadar() }
                    )
                }
            } else null,
            if (storedTechnique != AccessTechnique.Technique.POINT_SCAN &&
                !scanSettings.isDirectionalScanMode()
            ) {
                MenuItemRegistry.getDefinition(
                    MenuConstants.MenuIds.SETTINGS_MENU,
                    MenuConstants.ItemIds.Settings.SWITCH_TO_POINT_SCAN
                )?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { MenuManager.getInstance().switchToPointScan() }
                    )
                }
            } else null,
            if (CameraPermissionManager.getInstance(accessibilityService).hasPermission()) {
                MenuItemRegistry.getDefinition(
                    MenuConstants.MenuIds.SETTINGS_MENU,
                    MenuConstants.ItemIds.Settings.TOGGLE_HEAD_CONTROL
                )?.let { def ->
                    val settings = HeadControlSettings(accessibilityService)
                    val currentlyEnabled = settings.isHeadControlEnabled()
                    val stateLabel = accessibilityService.getString(
                        if (currentlyEnabled) R.string.menu_item_disable_head_control
                        else R.string.menu_item_enable_head_control
                    )
                    MenuItem(
                        id = def.id,
                        userProvidedText = stateLabel,
                        descriptionResource = def.descriptionResource,
                        drawableId = def.drawableId,
                        action = {
                            val headControlService = ServiceCore.getHeadControlService()

                            val success = headControlService?.setEnabled(!currentlyEnabled) ?: false
                            if (success) {
                                settings.setHeadControlEnabled(!currentlyEnabled)
                            }

                            MenuManager.getInstance().closeMenuHierarchy()
                        }
                    )
                }
            } else null,
            MenuItemRegistry.getDefinition(
                MenuConstants.MenuIds.SETTINGS_MENU,
                MenuConstants.ItemIds.Settings.TOGGLE_GROUP_SCAN
            )?.let { def ->
                val currentlyEnabled = scanSettings.isGroupScanEnabled()
                val stateLabel = accessibilityService.getString(
                    if (currentlyEnabled) R.string.menu_item_turn_group_scan_off
                    else R.string.menu_item_turn_group_scan_on
                )
                MenuItem(
                    id = def.id,
                    userProvidedText = stateLabel,
                    descriptionResource = def.descriptionResource,
                    drawableId = def.drawableId,
                    action = {
                        val prefManager = PreferenceManager(accessibilityService)
                        prefManager.setBooleanValue(
                            PreferenceManager.PREFERENCE_KEY_GROUP_SCAN,
                            !currentlyEnabled
                        )
                        MenuManager.getInstance().closeMenuHierarchy()
                    }
                )
            },
            MenuItemRegistry.getDefinition(
                MenuConstants.MenuIds.SETTINGS_MENU,
                MenuConstants.ItemIds.Settings.TOGGLE_GESTURE_REPEAT
            )?.let { def ->
                val currentlyEnabled = GestureRepeatManager.instance.isAutoRepeatEnabled()
                val stateLabel = accessibilityService.getString(
                    if (currentlyEnabled) R.string.menu_item_turn_gesture_repeat_off
                    else R.string.menu_item_turn_gesture_repeat_on
                )
                MenuItem(
                    id = def.id,
                    userProvidedText = stateLabel,
                    descriptionResource = def.descriptionResource,
                    drawableId = def.drawableId,
                    action = {
                        GestureRepeatManager.instance.toggleAutoRepeat(
                            context = accessibilityService,
                            syncGestureLock = true
                        )
                        MenuManager.getInstance().closeMenuHierarchy()
                    }
                )
            },
            MenuItemRegistry.getDefinition(
                MenuConstants.MenuIds.SETTINGS_MENU,
                MenuConstants.ItemIds.Settings.TOGGLE_GESTURE_LOCK_REARM
            )?.let { def ->
                val currentlyEnabled = GestureLockManager.instance.isAutoReenableEnabled()
                val stateLabel = accessibilityService.getString(
                    if (currentlyEnabled) R.string.menu_item_turn_gesture_lock_rearm_off
                    else R.string.menu_item_turn_gesture_lock_rearm_on
                )
                MenuItem(
                    id = def.id,
                    userProvidedText = stateLabel,
                    descriptionResource = def.descriptionResource,
                    drawableId = def.drawableId,
                    action = {
                        GestureLockManager.instance.toggleAutoReenable(
                            context = accessibilityService,
                            syncGestureLock = true
                        )
                        MenuManager.getInstance().closeMenuHierarchy()
                    }
                )
            }
        ),
        context = accessibilityService,
        coroutineScope = coroutineScope
        )
    }
}
