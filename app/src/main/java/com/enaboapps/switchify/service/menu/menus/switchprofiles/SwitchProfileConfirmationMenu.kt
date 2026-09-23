package com.enaboapps.switchify.service.menu.menus.switchprofiles

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants

class SwitchProfileConfirmationMenu(
    accessibilityService: SwitchifyAccessibilityService,
    profileName: String
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = listOf(
        MenuItem(
            id = "activate_switch_profile",
            userProvidedText = accessibilityService.getString(
                R.string.switch_profile_confirmation_activate,
                profileName
            ),
            descriptionResource = R.string.switch_profile_confirmation_activate_description,
            drawableId = R.drawable.ic_confirm,
            closeOnSelect = false,
            requiresScanningSelection = true,
            onRejectedTouchSelection = {
                ServiceCore.getSwitchProfileActivationCoordinator()?.repeatMenuPrompt()
            },
            action = {
                ServiceCore.getSwitchProfileActivationCoordinator()?.confirmFromMenu()
            }
        ),
        MenuItem(
            id = "cancel_switch_profile_activation",
            labelResource = R.string.menu_item_cancel,
            descriptionResource = R.string.switch_profile_confirmation_cancel_description,
            drawableId = R.drawable.ic_cancel,
            closeOnSelect = false,
            action = {
                ServiceCore.getSwitchProfileActivationCoordinator()?.cancel()
            }
        )
    ),
    menuId = MenuConstants.MenuIds.SWITCH_PROFILE_CONFIRMATION_MENU,
    showNavMenuItems = false
)
