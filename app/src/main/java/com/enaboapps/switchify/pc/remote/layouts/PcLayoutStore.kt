package com.enaboapps.switchify.pc.remote.layouts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

typealias PcSurfaceLayouts = Map<PcLayoutSurface, Map<String, PcButtonLayout>>

interface PcLayoutStore {
    val layouts: StateFlow<PcSurfaceLayouts>

    suspend fun load() {}

    suspend fun save(surface: PcLayoutSurface, section: String, layout: PcButtonLayout?)
}

class InMemoryPcLayoutStore(initial: PcSurfaceLayouts = emptyMap()) : PcLayoutStore {
    private val _layouts = MutableStateFlow(initial)
    override val layouts: StateFlow<PcSurfaceLayouts> = _layouts.asStateFlow()

    override suspend fun save(surface: PcLayoutSurface, section: String, layout: PcButtonLayout?) {
        require(PcLayoutSections.get(surface, section) != null) { INVALID }
        require(layout == null || PcLayoutSections.isValidSectionLayout(surface, section, layout)) { INVALID }
        _layouts.value = PcLayoutCodec.withSection(_layouts.value, surface, section, layout)
    }

    private companion object {
        const val INVALID = "Invalid section layout"
    }
}

object PcLayoutCodec {
    const val VERSION = 2
    const val MAX_LENGTH = 128_000

    fun decode(text: String?): PcSurfaceLayouts {
        if (text == null || text.length > MAX_LENGTH) return emptyMap()
        return try {
            val root = JSONObject(text)
            if (root.opt("version") != VERSION) return emptyMap()
            val layouts = root.optJSONObject("layouts") ?: return emptyMap()
            val result = linkedMapOf<PcLayoutSurface, Map<String, PcButtonLayout>>()
            for (surface in PcLayoutSurface.entries) {
                val sections = layouts.optJSONObject(surface.storageKey) ?: continue
                val decoded = linkedMapOf<String, PcButtonLayout>()
                for (definition in PcLayoutSections.definitions[surface].orEmpty()) {
                    val layout = decodeLayout(sections.optJSONObject(definition.key)) ?: continue
                    if (PcLayoutSections.isValidSectionLayout(surface, definition.key, layout)) decoded[definition.key] = layout
                }
                if (decoded.isNotEmpty()) result[surface] = decoded
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encode(layouts: PcSurfaceLayouts): String {
        val surfaces = JSONObject()
        layouts.forEach { (surface, sections) ->
            val encoded = JSONObject()
            sections.forEach { (section, layout) ->
                encoded.put(
                    section,
                    JSONObject()
                        .put("columns", layout.columns)
                        .put("cells", JSONArray().apply { layout.cells.forEach { put(it ?: JSONObject.NULL) } })
                )
            }
            surfaces.put(surface.storageKey, encoded)
        }
        return JSONObject().put("version", VERSION).put("layouts", surfaces).toString()
    }

    fun withSection(
        layouts: PcSurfaceLayouts,
        surface: PcLayoutSurface,
        section: String,
        layout: PcButtonLayout?
    ): PcSurfaceLayouts {
        val sections = layouts[surface].orEmpty().toMutableMap()
        if (layout != null) sections[section] = layout.copy(cells = layout.cells.toList()) else sections.remove(section)
        val next = layouts.toMutableMap()
        if (sections.isNotEmpty()) next[surface] = sections else next.remove(surface)
        return next
    }

    private fun decodeLayout(value: JSONObject?): PcButtonLayout? {
        if (value == null) return null
        val columns = value.opt("columns") as? Int ?: return null
        val cells = value.optJSONArray("cells") ?: return null
        val ids = (0 until cells.length()).map { index ->
            when (val cell = cells.opt(index)) {
                JSONObject.NULL -> null
                is String -> cell
                else -> return null
            }
        }
        return PcButtonLayout(columns, ids)
    }
}
