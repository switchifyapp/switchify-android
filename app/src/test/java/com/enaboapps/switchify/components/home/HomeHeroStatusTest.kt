package com.enaboapps.switchify.components.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeroStatusTest {
    @Test
    fun disabledStatusTakesPriorityOverConfiguration() {
        assertEquals(
            HomeHeroStatus.Disabled,
            resolveHomeHeroStatus(
                isAccessibilityServiceEnabled = false,
                isConfigValid = false,
                activeProfileName = "Desk controls"
            )
        )
    }

    @Test
    fun validConfigurationShowsFullActiveProfileName() {
        val profileName = "Living room wheelchair controls"

        assertEquals(
            HomeHeroStatus.Active(profileName),
            resolveHomeHeroStatus(
                isAccessibilityServiceEnabled = true,
                isConfigValid = true,
                activeProfileName = profileName
            )
        )
    }

    @Test
    fun incompleteConfigurationMarksActiveProfileForAttention() {
        assertEquals(
            HomeHeroStatus.ActiveNeedsAttention("Desk controls"),
            resolveHomeHeroStatus(
                isAccessibilityServiceEnabled = true,
                isConfigValid = false,
                activeProfileName = "Desk controls"
            )
        )
    }
}
