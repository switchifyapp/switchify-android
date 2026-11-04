package com.enaboapps.switchify.screens.settings.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchRankerTest {

    private fun item(
        title: String,
        summary: String? = null,
        explanation: String? = null,
        path: List<String> = emptyList(),
        route: String = "Route"
    ) = SettingsSearchItem(
        entry = SettingsSearchEntry(route, null, null, null, title.hashCode(), null, null),
        title = title,
        summary = summary,
        explanation = explanation,
        path = path
    )

    private val items = listOf(
        item("Scan Speeds", "How fast the highlight moves", path = listOf("Settings", "Scanning Mode")),
        item("Auto select delay", "0.1 to 100 seconds", path = listOf("Settings", "Selection")),
        item("Pause Settings", "Configure pause and resume behaviour", path = listOf("Settings", "Behaviour")),
        item("Switch Hold", explanation = "Hold a switch to repeat its action", path = listOf("Settings", "Input")),
        item("Résumé speed", path = listOf("Settings"))
    )

    @Test
    fun blankQueryReturnsNothing() {
        assertTrue(SettingsSearchRanker.rank("   ", items).isEmpty())
    }

    @Test
    fun titlePrefixOutranksSummaryMatch() {
        val ranked = SettingsSearchRanker.rank("pause", items)

        assertEquals(listOf("Pause Settings"), ranked.map { it.title })
    }

    @Test
    fun everyTokenMustMatchSomewhere() {
        val ranked = SettingsSearchRanker.rank("switch hold repeat", items)

        assertEquals(listOf("Switch Hold"), ranked.map { it.title })
        assertTrue(SettingsSearchRanker.rank("switch hold nonsense", items).isEmpty())
    }

    @Test
    fun pathMatchesFindSettingsOnAScreen() {
        val ranked = SettingsSearchRanker.rank("selection", items)

        assertEquals(listOf("Auto select delay"), ranked.map { it.title })
    }

    @Test
    fun equalScoresFallBackToTitleOrder() {
        val ranked = SettingsSearchRanker.rank("speed", items)

        assertEquals(listOf("Résumé speed", "Scan Speeds"), ranked.map { it.title })
    }

    @Test
    fun diacriticsAndCaseAreIgnored() {
        val ranked = SettingsSearchRanker.rank("RESUME", items)

        assertEquals(listOf("Résumé speed", "Pause Settings"), ranked.map { it.title })
    }
}
