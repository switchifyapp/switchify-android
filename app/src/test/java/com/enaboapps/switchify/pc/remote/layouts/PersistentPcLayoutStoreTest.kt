package com.enaboapps.switchify.pc.remote.layouts

import com.enaboapps.switchify.pc.storage.InMemoryPcKeyValueStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PersistentPcLayoutStoreTest {
    private val storage = InMemoryPcKeyValueStore()
    private val key = PersistentPcLayoutStore.KEY
    private val click = PcButtonLayout.initial(listOf("click.double"))
    private val speed = PcButtonLayout.initial(listOf("speed.slower"))
    private val enter = PcButtonLayout.initial(listOf("key.Enter"))

    @Test
    fun usesRemoteStorageKey() {
        assertEquals("switchify.remote.layouts.v2", key)
    }

    @Test
    fun persistsAndResetsOnlyTheChosenSectionIncludingConcurrentSaves() = runTest {
        val store = PersistentPcLayoutStore(storage)
        listOf(
            async { store.save(PcLayoutSurface.Mouse, "clicks", click) },
            async { store.save(PcLayoutSurface.Mouse, "speed", speed) },
            async { store.save(PcLayoutSurface.Typing, "keys", enter) }
        ).awaitAll()
        val restarted = PersistentPcLayoutStore(storage)
        restarted.load()
        assertEquals(store.layouts.value, restarted.layouts.value)
        restarted.save(PcLayoutSurface.Mouse, "speed", null)
        assertEquals(
            mapOf(
                PcLayoutSurface.Mouse to mapOf("clicks" to click),
                PcLayoutSurface.Typing to mapOf("keys" to enter)
            ),
            restarted.layouts.value
        )
    }

    @Test
    fun keepsTheSavedValueAfterAStorageFailureAndPermitsRetry() = runTest {
        val store = PersistentPcLayoutStore(storage)
        store.save(PcLayoutSurface.Mouse, "clicks", click)
        var failNext = true
        storage.failPut = { _, _ -> failNext.also { failNext = false } }
        try {
            store.save(PcLayoutSurface.Mouse, "speed", speed)
            fail("Expected the write to fail")
        } catch (_: IllegalStateException) {
        }
        assertEquals(mapOf(PcLayoutSurface.Mouse to mapOf("clicks" to click)), store.layouts.value)
        store.save(PcLayoutSurface.Mouse, "speed", speed)
        assertEquals(speed, store.layouts.value[PcLayoutSurface.Mouse]?.get("speed"))
    }

    @Test
    fun ignoresV1LayoutsWithoutAlteringOtherStorage() = runTest {
        val legacy = JSONObject().put("version", 1).put("layouts", JSONObject().put("mouse", encode(click))).toString()
        storage.values["switchify.remote.layouts.v1"] = legacy
        storage.values["unrelated.preference"] = "preserved"
        val store = PersistentPcLayoutStore(storage)
        store.load()
        assertTrue(store.layouts.value.isEmpty())
        store.save(PcLayoutSurface.Mouse, "speed", speed)
        assertEquals("preserved", storage.values["unrelated.preference"])
        assertEquals(legacy, storage.values["switchify.remote.layouts.v1"])
    }

    @Test
    fun fallsBackIndependentlyForBadSectionsDuplicatesAndForeignButtons() = runTest {
        storage.values[key] = JSONObject()
            .put("version", 2)
            .put(
                "layouts",
                JSONObject()
                    .put(
                        "mouse",
                        JSONObject()
                            .put("speed", encode(speed))
                            .put("clicks", encode(PcButtonLayout(1, listOf("click.double", "click.double"))))
                            .put("movement", encode(PcButtonLayout.initial(listOf("draft.clear"))))
                            .put("unknown", encode(speed))
                    )
                    .put("typing", JSONObject().put("keys", encode(enter)))
            )
            .toString()
        val store = PersistentPcLayoutStore(storage)
        store.load()
        assertEquals(
            mapOf(
                PcLayoutSurface.Mouse to mapOf("speed" to speed),
                PcLayoutSurface.Typing to mapOf("keys" to enter)
            ),
            store.layouts.value
        )
        assertInvalid { store.save(PcLayoutSurface.Mouse, "speed", PcButtonLayout.initial(listOf("draft.clear"))) }
        assertInvalid { store.save(PcLayoutSurface.Mouse, "unknown", null) }
    }

    @Test
    fun fallsBackForInvalidStorage() = runTest {
        val oversized = JSONObject()
            .put("version", 2)
            .put("layouts", JSONObject().put("mouse", JSONObject().put("clicks", encode(click))))
            .put("pad", "x".repeat(PcLayoutCodec.MAX_LENGTH))
            .toString()
        listOf(
            "{",
            """{"version":1,"layouts":{}}""",
            """{"version":2,"layouts":null}""",
            """{"version":"2","layouts":{"mouse":{"clicks":{"columns":1,"cells":["click.double"]}}}}""",
            """{"version":2,"layouts":{"mouse":{"clicks":{"columns":1.5,"cells":["click.double"]}}}}""",
            """{"version":2,"layouts":{"mouse":{"clicks":{"columns":1,"cells":[7]}}}}""",
            oversized
        ).forEach { raw ->
            storage.values[key] = raw
            val store = PersistentPcLayoutStore(storage)
            store.load()
            assertTrue(raw.take(60), store.layouts.value.isEmpty())
        }
    }

    @Test
    fun unreadableStorageLeavesOriginalSections() = runTest {
        storage.failGet = { true }
        val store = PersistentPcLayoutStore(storage)
        store.load()
        assertTrue(store.layouts.value.isEmpty())
    }

    @Test
    fun snapshotsCallerDataBeforeWriting() = runTest {
        val store = PersistentPcLayoutStore(storage)
        val cells = mutableListOf<String?>("click.double", null, null)
        store.save(PcLayoutSurface.Mouse, "clicks", PcButtonLayout(3, cells))
        cells[0] = "key.Enter"
        assertEquals(click, store.layouts.value[PcLayoutSurface.Mouse]?.get("clicks"))
    }

    @Test
    fun retainsV2GridsAndCrossSurfaceActionsThroughRestart() = runTest {
        val expected = mapOf(
            PcLayoutSurface.Mouse to mapOf(
                "movement" to PcButtonLayout(3, listOf("move.0.0", "move.1.0", null)),
                "clicks" to PcButtonLayout(1, listOf("key.Enter", null, "monitor.left"))
            ),
            PcLayoutSurface.Window to mapOf("windows" to PcButtonLayout(2, listOf("key.Enter", "scroll.up"))),
            PcLayoutSurface.Typing to mapOf("keys" to PcButtonLayout(1, listOf("draft.send", "window.closeFocused")))
        )
        val layouts = JSONObject()
        expected.forEach { (surface, sections) ->
            layouts.put(surface.storageKey, JSONObject().apply { sections.forEach { (name, layout) -> put(name, encode(layout)) } })
        }
        storage.values[key] = JSONObject().put("version", 2).put("layouts", layouts).toString()
        val store = PersistentPcLayoutStore(storage)
        store.load()
        assertEquals(expected, store.layouts.value)
        store.save(PcLayoutSurface.Mouse, "speed", PcButtonLayout(1, listOf("key.Enter")))
        val restarted = PersistentPcLayoutStore(storage)
        restarted.load()
        val mouse = expected.getValue(PcLayoutSurface.Mouse) + ("speed" to PcButtonLayout(1, listOf("key.Enter")))
        assertEquals(expected + (PcLayoutSurface.Mouse to mouse), restarted.layouts.value)
    }

    @Test
    fun writesRemoteCompatibleJson() = runTest {
        val store = PersistentPcLayoutStore(storage)
        store.save(PcLayoutSurface.Mouse, "clicks", PcButtonLayout(2, listOf("click.double", null)))
        val root = JSONObject(storage.values.getValue(key))
        assertEquals(setOf("version", "layouts"), root.keys().asSequence().toSet())
        assertEquals(2, root.getInt("version"))
        val section = root.getJSONObject("layouts").getJSONObject("mouse").getJSONObject("clicks")
        assertEquals(2, section.getInt("columns"))
        val cells = section.getJSONArray("cells")
        assertEquals("click.double", cells.getString(0))
        assertTrue(cells.isNull(1))
        store.save(PcLayoutSurface.Mouse, "clicks", null)
        assertEquals("""{"version":2,"layouts":{}}""", storage.values[key])
    }

    private fun encode(layout: PcButtonLayout): JSONObject =
        JSONObject()
            .put("columns", layout.columns)
            .put("cells", JSONArray().apply { layout.cells.forEach { put(it ?: JSONObject.NULL) } })

    private suspend fun assertInvalid(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected an invalid section layout")
        } catch (error: IllegalArgumentException) {
            assertEquals("Invalid section layout", error.message)
        }
    }
}
