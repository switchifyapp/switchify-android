package com.enaboapps.switchify.screens.settings.switches

import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.switches.profiles.SwitchProfile
import com.enaboapps.switchify.switches.profiles.SwitchProfileDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchProfileContextPolicyTest {
    private val active = SwitchProfile("active-id", "Everyday", emptyList())
    private val inactive = SwitchProfile("inactive-id", "Gaming", emptyList())
    private val document = SwitchProfileDocument(
        activeProfileId = active.id,
        profiles = listOf(active, inactive)
    )

    @Test
    fun unresolvedContextRemainsLoading() {
        val state = SwitchProfileContextPolicy.resolve(document, active.id, false)

        assertTrue(state.isLoading)
        assertFalse(state.isMissing)
    }

    @Test
    fun activeTargetIncludesItsNameAndStatus() {
        val state = SwitchProfileContextPolicy.resolve(document, active.id, true)

        assertEquals(active.id, state.targetProfileId)
        assertEquals("Everyday", state.profileName)
        assertTrue(state.isActive)
        assertFalse(state.isLoading)
    }

    @Test
    fun inactiveTargetRemainsTheTarget() {
        val state = SwitchProfileContextPolicy.resolve(document, inactive.id, true)

        assertEquals(inactive.id, state.targetProfileId)
        assertEquals("Gaming", state.profileName)
        assertFalse(state.isActive)
    }

    @Test
    fun renamedTargetUsesTheLatestName() {
        val renamed = document.copy(
            profiles = listOf(active, inactive.copy(name = "Console"))
        )

        val state = SwitchProfileContextPolicy.resolve(renamed, inactive.id, true)

        assertEquals("Console", state.profileName)
        assertEquals(inactive.id, state.targetProfileId)
    }

    @Test
    fun missingTargetIsReportedWithoutChangingItsId() {
        val state = SwitchProfileContextPolicy.resolve(document, "removed-id", true)

        assertEquals("removed-id", state.targetProfileId)
        assertTrue(state.isMissing)
        assertFalse(state.isLoading)
    }

    @Test
    fun nestedRoutesKeepTheProfileId() {
        val profileId = "profile-id"

        assertEquals(
            "${NavigationRoute.ExternalSwitches.name}/$profileId",
            SwitchProfileRoutes.externalSwitches(profileId)
        )
        assertEquals(
            "${NavigationRoute.EditExternalSwitch.name}/$profileId/42",
            SwitchProfileRoutes.editExternalSwitch(profileId, "42")
        )
        assertEquals(
            "${NavigationRoute.LongPressActions.name}/$profileId/42",
            SwitchProfileRoutes.longPressActions(profileId, "42")
        )
        assertEquals(
            "${NavigationRoute.SwitchActionSelection.name}/$profileId/1",
            SwitchProfileRoutes.actionSelection(profileId, 1)
        )
    }
}
