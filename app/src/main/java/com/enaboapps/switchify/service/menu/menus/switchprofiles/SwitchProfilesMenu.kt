package com.enaboapps.switchify.service.menu.menus.switchprofiles

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.switches.profiles.SwitchProfileRepository
import com.enaboapps.switchify.switches.profiles.SwitchProfileConfirmationMode

class SwitchProfilesMenu(
    accessibilityService: SwitchifyAccessibilityService
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = emptyList(),
    menuId = MenuConstants.MenuIds.SWITCH_PROFILES_MENU,
    dynamicLoad = {
        val repository = SwitchProfileRepository.getInstance(accessibilityService)
        repository.initialize()
        val activeProfileId = repository.document.value.activeProfileId
        repository.profiles().map { profile ->
            val active = profile.id == activeProfileId
            MenuItem(
                id = "switch_profile_${profile.id}",
                userProvidedText = profile.name,
                descriptionResource = null,
                userProvidedDescription = if (active) "Active profile" else "Test and activate profile",
                drawableId = com.enaboapps.switchify.R.drawable.ic_hand_switch_press,
                circleText = if (active) "✓" else null,
                closeOnSelect = false,
                action = {
                    if (!active) {
                        com.enaboapps.switchify.service.core.ServiceCore
                            .getSwitchProfileActivationCoordinator()
                            ?.begin(profile.id, SwitchProfileConfirmationMode.MENU)
                    }
                }
            )
        }
    },
    showNavMenuItems = true
)
