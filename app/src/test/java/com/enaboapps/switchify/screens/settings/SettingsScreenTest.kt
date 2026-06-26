package com.enaboapps.switchify.screens.settings

import com.enaboapps.switchify.R
import com.enaboapps.switchify.nav.NavigationRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreenTest {
    @Test
    fun pcSettingsLinkUsesGeneralPcSettingsCopyAndRoute() {
        val spec = pcSettingsRouteLinkSpec()

        assertEquals(R.string.pc_settings_title, spec.titleResId)
        assertEquals(R.string.pc_settings_link_summary, spec.summaryResId)
        assertEquals(NavigationRoute.PcSettings.name, spec.route)
    }
}
