package com.enaboapps.switchify.pc.remote.layouts

import com.enaboapps.switchify.pc.remote.actions.PcActionCatalog
import com.enaboapps.switchify.pc.remote.actions.PcActionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcActionPickerModelTest {
    private val categories = mapOf(
        PcActionCategory.PointerMovement to "Pointer movement",
        PcActionCategory.MouseButtons to "Mouse buttons",
        PcActionCategory.Scrolling to "Scrolling",
        PcActionCategory.PointerSpeed to "Pointer speed",
        PcActionCategory.Monitors to "Monitors",
        PcActionCategory.Modifiers to "Modifiers",
        PcActionCategory.Windows to "Windows",
        PcActionCategory.Shortcuts to "Shortcuts",
        PcActionCategory.PcKeys to "PC keys",
        PcActionCategory.DraftText to "Draft text"
    )

    private val names = mapOf(
        "move.0.1" to "Move pointer left",
        "key.ArrowLeft" to "Arrow left key",
        "monitor.left" to "Move pointer to monitor left",
        "window.closeFocused" to "Close window",
        "modifier.Meta" to "Hold Windows modifier"
    )

    private val options = PcActionCatalog.actions.map { action ->
        PcActionOption(
            id = action.id,
            name = names[action.id] ?: action.id,
            category = categories.getValue(action.category),
            keywords = action.keywords
        )
    }

    private fun ids(query: String) = PcActionPickerModel.search(options, query).map { it.id }

    @Test
    fun searchesNamesCategoriesAndKeywordsCaseInsensitively() {
        assertEquals(listOf("key.ArrowLeft"), ids("ARROW LEFT"))
        assertTrue("monitor.left" in ids("display"))
        assertTrue("move.0.1" !in ids("display"))
        assertEquals(listOf("window.closeFocused"), ids("close window"))
        assertTrue("modifier.Meta" in ids("command"))
        assertEquals(PcActionCatalog.actions.filter { it.category == PcActionCategory.PcKeys }.map { it.id }, ids("pc keys"))
        assertTrue(ids("no such action").isEmpty())
    }

    @Test
    fun blankQueriesKeepEveryOptionInCatalogOrder() {
        assertEquals(options.map { it.id }, ids(""))
        assertEquals(options.map { it.id }, ids("   "))
    }

    @Test
    fun groupsByCategoryInFirstAppearanceOrder() {
        val groups = PcActionPickerModel.group(options)
        assertEquals(categories.values.toList(), groups.map { it.category })
        assertEquals(options.size, groups.sumOf { it.options.size })
        assertEquals("move.1.1", groups[1].options.first().id)
    }

    @Test
    fun everyCategoryHasALabel() {
        PcActionCategory.entries.forEach { PcActionPickerModel.categoryLabel(it) }
    }
}
