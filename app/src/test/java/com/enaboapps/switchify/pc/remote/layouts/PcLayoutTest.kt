package com.enaboapps.switchify.pc.remote.layouts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PcLayoutTest {
    @Test
    fun preservesEmptyCellsAndSwapsOccupiedCells() {
        val initial = PcButtonLayout.initial(listOf("a", "b"))
        assertEquals(PcButtonLayout(3, listOf("a", "b", null)), initial)
        assertEquals(listOf("b", "a", null), PcButtonLayout.moveCell(initial, 0, 1).cells)
        assertEquals(listOf(null, "b", "a"), PcButtonLayout.moveCell(initial, 0, 2).cells)
        assertEquals(listOf("a", "b", null), initial.cells)
    }

    @Test
    fun removesAndRestoresControlsWithoutDuplicates() {
        val layout = PcButtonLayout.initial(listOf("a", "b"))
        assertSame(layout, PcButtonLayout.setCell(layout, 2, "a"))
        val removed = PcButtonLayout.setCell(layout, 0, null)
        assertEquals(listOf(null, "b", "a"), PcButtonLayout.setCell(removed, 2, "a").cells)
    }

    @Test
    fun insertsAndRemovesColumns() {
        val layout = PcButtonLayout.initial(listOf("a", "b", "c", "d"))
        val inserted = PcButtonLayout.resize(layout, PcLayoutAxis.Column, 1, true)
        assertEquals(PcButtonLayout(4, listOf("a", null, "b", "c", "d", null, null, null)), inserted)
        assertEquals(layout, PcButtonLayout.resize(inserted, PcLayoutAxis.Column, 1, false))
        assertEquals(PcButtonLayout(2, listOf("b", "c", null, null)), PcButtonLayout.resize(layout, PcLayoutAxis.Column, 0, false))
    }

    @Test
    fun insertsAndRemovesRowsWithinBounds() {
        val layout = PcButtonLayout.initial(listOf("a"))
        assertEquals(listOf(null, null, null, "a", null, null), PcButtonLayout.resize(layout, PcLayoutAxis.Row, 0, true).cells)
        assertSame(layout, PcButtonLayout.resize(layout, PcLayoutAxis.Row, 0, false))
        val full = PcButtonLayout(4, List(80) { null })
        assertSame(full, PcButtonLayout.resize(full, PcLayoutAxis.Row, 20, true))
        assertSame(full, PcButtonLayout.resize(full, PcLayoutAxis.Column, 4, true))
    }

    @Test
    fun rejectsMalformedLayouts() {
        listOf(
            null,
            PcButtonLayout(0, emptyList()),
            PcButtonLayout(2, listOf("a")),
            PcButtonLayout(1, listOf("a", "a")),
            PcButtonLayout(1, listOf("bad id")),
            PcButtonLayout(1, List(21) { null }),
            PcButtonLayout(5, List(5) { null })
        ).forEach { assertFalse(it.toString(), PcButtonLayout.isValid(it)) }
    }

    @Test
    fun reordersCompleteTracks() {
        val layout = PcButtonLayout(3, listOf("a", null, "b", "c", "d", null, null, "e", "f"))
        assertEquals(listOf("c", "d", null, null, "e", "f", "a", null, "b"), PcButtonLayout.moveTrack(layout, PcLayoutAxis.Row, 0, 2).cells)
        assertEquals(listOf(null, "e", "f", "a", null, "b", "c", "d", null), PcButtonLayout.moveTrack(layout, PcLayoutAxis.Row, 2, 0).cells)
        val moved = PcButtonLayout.moveTrack(layout, PcLayoutAxis.Column, 0, 2)
        assertEquals(listOf(null, "b", "a", "d", null, "c", "e", "f", null), moved.cells)
        assertEquals(layout, PcButtonLayout.moveTrack(moved, PcLayoutAxis.Column, 2, 0))
        assertSame(layout, PcButtonLayout.moveTrack(layout, PcLayoutAxis.Row, -1, 0))
        assertSame(layout, PcButtonLayout.moveTrack(layout, PcLayoutAxis.Column, 1, 3))
    }

    @Test
    fun capturesResponsiveRowsInEachSection() {
        val clicks = PcLayoutSections.get(PcLayoutSurface.Mouse, PcLayoutSections.MOUSE_CLICKS)!!
        assertEquals(
            PcButtonLayout(3, listOf("click.double", "click.right", "drag.toggle", "scroll.up", "scroll.down", null)),
            PcLayoutSections.sectionDefault(clicks, 460f, 1f)
        )
        assertEquals(
            PcButtonLayout(2, listOf("click.double", "click.right", "drag.toggle", null, "scroll.up", "scroll.down")),
            PcLayoutSections.sectionDefault(clicks, 300f, 1f)
        )
        val movement = PcLayoutSections.get(PcLayoutSurface.Mouse, PcLayoutSections.MOUSE_MOVEMENT)!!
        assertEquals(3, PcLayoutSections.sectionDefault(movement, 300f, 2f).columns)
    }

    @Test
    fun acceptsCatalogActionsAcrossSectionsButEnforcesPlacement() {
        assertTrue(PcLayoutSections.isValidSectionLayout(PcLayoutSurface.Mouse, "clicks", PcButtonLayout(1, listOf("click.double"))))
        assertTrue(PcLayoutSections.isValidSectionLayout(PcLayoutSurface.Mouse, "speed", PcButtonLayout(1, listOf("click.double"))))
        assertTrue(PcLayoutSections.isValidSectionLayout(PcLayoutSurface.Window, "modifiers", PcButtonLayout(1, listOf("key.Enter"))))
        assertFalse(PcLayoutSections.isValidSectionLayout(PcLayoutSurface.Window, "modifiers", PcButtonLayout(1, listOf("draft.clear"))))
        assertFalse(PcLayoutSections.isValidSectionLayout(PcLayoutSurface.Mouse, "unknown", PcButtonLayout(1, listOf("click.double"))))
        assertNull(PcLayoutSections.get(PcLayoutSurface.Mouse, "__proto__"))
    }

    @Test
    fun computesResponsiveColumnsLikeRemote() {
        assertEquals(1, PcLayoutSections.gridColumns(0f, 140, 8, 1f))
        assertEquals(3, PcLayoutSections.gridColumns(460f, 140, 8, 1f))
        assertEquals(2, PcLayoutSections.gridColumns(460f, 140, 8, 1f, 2))
        assertEquals(2, PcLayoutSections.gridColumns(360f, 140, 8, 1.5f))
    }

    @Test
    fun fitsSavedColumnsAndPreservesTargetsWhenScrolling() {
        for (width in listOf(280f, 296f, 320f, 350f, 390f, 600f, 960f)) {
            for (columns in 1..4) {
                for (handles in listOf(0, 48)) {
                    val result = PcLayoutSections.gridMetrics(width, columns, 8, handles)
                    assertTrue(result.cellWidth >= 48)
                    assertTrue(result.gridWidth <= width)
                    assertFalse(result.overflows)
                }
            }
        }
        assertEquals(PcGridMetrics(48, 272, true), PcLayoutSections.gridMetrics(200f, 4, 8, 48))
        assertEquals(PcGridMetrics(48, 216, true), PcLayoutSections.gridMetrics(215f, 4, 8))
        assertEquals(48, PcLayoutSections.gridMetrics(0f, 3, 8).cellWidth)
    }

    @Test
    fun decodesV2LayoutsAndFallsBackPerSection() {
        val text = """
            {"version":2,"layouts":{
              "mouse":{"clicks":{"columns":2,"cells":["click.double",null]},"speed":{"columns":1,"cells":["a","a"]}},
              "window":{"modifiers":{"columns":1,"cells":["draft.clear"]},"windows":{"columns":1,"cells":["key.Enter"]}},
              "typing":{"draft":{"columns":1,"cells":["draft.send"]}}
            }}
        """.trimIndent()
        val decoded = PcLayoutCodec.decode(text)
        assertEquals(PcButtonLayout(2, listOf("click.double", null)), decoded[PcLayoutSurface.Mouse]?.get("clicks"))
        assertNull(decoded[PcLayoutSurface.Mouse]?.get("speed"))
        assertNull(decoded[PcLayoutSurface.Window]?.get("modifiers"))
        assertEquals(PcButtonLayout(1, listOf("key.Enter")), decoded[PcLayoutSurface.Window]?.get("windows"))
        assertEquals(PcButtonLayout(1, listOf("draft.send")), decoded[PcLayoutSurface.Typing]?.get("draft"))
        assertEquals(decoded, PcLayoutCodec.decode(PcLayoutCodec.encode(decoded)))
        assertTrue(PcLayoutCodec.decode("{\"version\":1,\"layouts\":{}}").isEmpty())
        assertTrue(PcLayoutCodec.decode("not json").isEmpty())
    }

    @Test
    fun inMemoryStoreSavesAndResetsOnlyTheChosenSection() = runTest {
        val store = InMemoryPcLayoutStore()
        store.save(PcLayoutSurface.Mouse, "clicks", PcButtonLayout(1, listOf("click.right")))
        store.save(PcLayoutSurface.Mouse, "speed", PcButtonLayout(1, listOf("speed.faster")))
        store.save(PcLayoutSurface.Mouse, "clicks", null)
        assertEquals(mapOf("speed" to PcButtonLayout(1, listOf("speed.faster"))), store.layouts.value[PcLayoutSurface.Mouse])
        val rejected = runCatching { store.save(PcLayoutSurface.Mouse, "clicks", PcButtonLayout(1, listOf("draft.send"))) }
        assertTrue(rejected.isFailure)
    }
}
