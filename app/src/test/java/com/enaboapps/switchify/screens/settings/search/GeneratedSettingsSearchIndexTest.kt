package com.enaboapps.switchify.screens.settings.search

import com.enaboapps.switchify.R
import com.enaboapps.switchify.nav.NavigationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedSettingsSearchIndexTest {

    private val entries = GeneratedSettingsSearchIndex.entries

    @Test
    fun indexCoversTheSettingsTabsAndTheirSubScreens() {
        val routes = entries.map { it.route }.toSet()

        assertTrue(NavigationRoute.Settings.name in routes)
        assertTrue(NavigationRoute.AutoScanSettings.name in routes)
        assertTrue(NavigationRoute.PauseSettings.name in routes)
        assertTrue(NavigationRoute.ScrollingSettings.name in routes)
        assertTrue(NavigationRoute.SwitchHold.name in routes)
    }

    @Test
    fun settingsScreenRowsKnowTheirTab() {
        val onSettings = entries.filter { it.route == NavigationRoute.Settings.name }

        assertTrue(onSettings.isNotEmpty())
        assertTrue(onSettings.all { it.tab != null })
        assertEquals(setOf(0, 1, 2, 3), onSettings.mapNotNull { it.tab }.toSet())
    }

    @Test
    fun knownRowsResolveToTheirScreenAndSection() {
        val autoStart = entries.single { it.titleResId == R.string.preference_title_auto_start_scanning }

        assertEquals(NavigationRoute.AutoScanSettings.name, autoStart.route)
        assertEquals(R.string.screen_title_auto_scan_settings, autoStart.screenTitleResId)
        assertEquals(R.string.section_title_auto_scan_behavior, autoStart.sectionTitleResId)
        assertEquals(R.string.preference_summary_auto_start_scanning, autoStart.summaryResId)
    }

    @Test
    fun conditionalTitlesProduceOneEntryEach() {
        val account = entries.filter {
            it.titleResId == R.string.settings_title_account || it.titleResId == R.string.settings_title_sign_in
        }

        assertEquals(2, account.size)
        assertEquals(
            setOf(R.string.settings_summary_account, R.string.settings_summary_sign_in),
            account.map { it.summaryResId }.toSet()
        )
    }

    @Test
    fun entriesAreUniquePerRouteTabAndTitle() {
        val keys = entries.map { Triple(it.route, it.tab, it.titleResId) }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun noEntryPointsAtAParameterisedRoute() {
        assertTrue(entries.none { "/" in it.route || "{" in it.route })
    }
}
