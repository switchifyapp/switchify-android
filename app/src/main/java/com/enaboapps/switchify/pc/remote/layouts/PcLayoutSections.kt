package com.enaboapps.switchify.pc.remote.layouts

import androidx.annotation.StringRes
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.remote.actions.PcActionCatalog
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PcSectionGroup(
    val ids: List<String>,
    val minItemWidth: Int,
    val maxColumns: Int? = null,
    val exactColumns: Int? = null,
    val gap: Int? = null
)

data class PcSectionDefinition(
    val key: String,
    @StringRes val titleRes: Int,
    val groups: List<PcSectionGroup>
)

data class PcGridMetrics(val cellWidth: Int, val gridWidth: Int, val overflows: Boolean)

object PcLayoutSections {
    const val MOUSE_MOVEMENT = "movement"
    const val MOUSE_CLICKS = "clicks"
    const val MOUSE_SPEED = "speed"
    const val MONITORS = "monitors"
    const val TYPING_DRAFT = "draft"
    const val TYPING_KEYS = "keys"
    const val WINDOW_MODIFIERS = "modifiers"
    const val WINDOW_WINDOWS = "windows"
    const val WINDOW_SHORTCUTS = "shortcuts"

    const val DEFAULT_GAP = 8
    const val MIN_CELL_WIDTH = 48

    private val monitorIds = PcActionCatalog.monitorDirections.map { "monitor.${it.protocolValue}" }

    val definitions: Map<PcLayoutSurface, List<PcSectionDefinition>> = mapOf(
        PcLayoutSurface.Mouse to listOf(
            PcSectionDefinition(
                MOUSE_MOVEMENT,
                R.string.pc_section_movement,
                listOf(
                    PcSectionGroup(
                        ids = listOf(
                            "move.0.0", "move.1.0", "move.2.0",
                            "move.0.1", "move.1.1", "move.2.1",
                            "move.0.2", "move.1.2", "move.2.2"
                        ),
                        minItemWidth = 48,
                        exactColumns = 3,
                        gap = 10
                    )
                )
            ),
            PcSectionDefinition(
                MOUSE_CLICKS,
                R.string.pc_section_clicks,
                listOf(
                    PcSectionGroup(listOf("click.double", "click.right", "drag.toggle"), minItemWidth = 140),
                    PcSectionGroup(listOf("scroll.up", "scroll.down"), minItemWidth = 140, maxColumns = 2)
                )
            ),
            PcSectionDefinition(
                MOUSE_SPEED,
                R.string.pc_section_speed,
                listOf(PcSectionGroup(listOf("speed.slower", "speed.faster"), minItemWidth = 120, maxColumns = 2))
            ),
            PcSectionDefinition(
                MONITORS,
                R.string.pc_section_monitors,
                listOf(PcSectionGroup(monitorIds, minItemWidth = 96, maxColumns = 4))
            )
        ),
        PcLayoutSurface.Typing to listOf(
            PcSectionDefinition(
                TYPING_DRAFT,
                R.string.pc_section_draft,
                listOf(PcSectionGroup(listOf("draft.clear", "draft.send"), minItemWidth = 130, maxColumns = 2))
            ),
            PcSectionDefinition(
                TYPING_KEYS,
                R.string.pc_section_keys,
                listOf(PcSectionGroup(PcActionCatalog.pcKeys.map { "key.$it" }, minItemWidth = 80, maxColumns = 4))
            )
        ),
        PcLayoutSurface.Window to listOf(
            PcSectionDefinition(
                WINDOW_MODIFIERS,
                R.string.pc_section_modifiers,
                listOf(PcSectionGroup(PcActionCatalog.modifierKeys.map { "modifier.$it" }, minItemWidth = 120))
            ),
            PcSectionDefinition(
                WINDOW_WINDOWS,
                R.string.pc_section_windows,
                listOf(PcSectionGroup(PcActionCatalog.windowActions.map { "window.${it.first}" }, minItemWidth = 130))
            ),
            PcSectionDefinition(
                WINDOW_SHORTCUTS,
                R.string.pc_section_shortcuts,
                listOf(PcSectionGroup(PcActionCatalog.shortcutKeys.map { "shortcut.$it" }, minItemWidth = 96))
            ),
            PcSectionDefinition(
                MONITORS,
                R.string.pc_section_window_monitors,
                listOf(PcSectionGroup(monitorIds, minItemWidth = 96, maxColumns = 4))
            )
        )
    )

    fun get(surface: PcLayoutSurface, section: String): PcSectionDefinition? =
        definitions[surface]?.firstOrNull { it.key == section }

    fun isValidSectionLayout(surface: PcLayoutSurface, section: String, layout: PcButtonLayout?): Boolean {
        if (get(surface, section) == null || layout == null || !PcButtonLayout.isValid(layout)) return false
        return layout.cells.all { id -> id == null || PcActionCatalog.canPlace(id, surface) }
    }

    fun gridColumns(width: Float, minItemWidth: Int, gap: Int, fontScale: Float, maxColumns: Int = Int.MAX_VALUE): Int {
        if (!width.isFinite() || width <= 0f) return 1
        val scale = if (fontScale.isFinite() && fontScale > 0f) max(1f, fontScale) else 1f
        val effectiveMinimum = (minItemWidth * (1 + (scale - 1) * 0.2f)).roundToInt()
        return max(1, min(maxColumns, floor((width + gap) / (effectiveMinimum + gap).toFloat()).toInt()))
    }

    fun groupColumns(group: PcSectionGroup, width: Float, fontScale: Float, gap: Int = DEFAULT_GAP): Int =
        group.exactColumns ?: gridColumns(
            width,
            group.minItemWidth,
            group.gap ?: gap,
            fontScale,
            group.maxColumns ?: Int.MAX_VALUE
        )

    fun sectionDefault(definition: PcSectionDefinition, width: Float, fontScale: Float, gap: Int = DEFAULT_GAP): PcButtonLayout {
        val groups = definition.groups.map { group ->
            group to (group.exactColumns ?: gridColumns(
                width,
                group.minItemWidth,
                group.gap ?: gap,
                fontScale,
                min(PcButtonLayout.MAX_COLUMNS, group.maxColumns ?: group.ids.size)
            ))
        }
        val columns = groups.maxOf { it.second }
        val cells = mutableListOf<String?>()
        groups.forEach { (group, groupColumns) ->
            group.ids.chunked(groupColumns).forEach { chunk ->
                cells += chunk
                repeat(columns - chunk.size) { cells += null }
            }
        }
        return PcButtonLayout(columns, cells)
    }

    fun gridMetrics(viewportWidth: Float, columns: Int, gap: Int = DEFAULT_GAP, handleWidth: Int = 0): PcGridMetrics {
        val width = if (viewportWidth.isFinite()) max(0f, viewportWidth) else 0f
        val gaps = gap * (columns - 1 + if (handleWidth > 0) 1 else 0)
        val cellWidth = max(MIN_CELL_WIDTH, floor((width - handleWidth - gaps) / columns).toInt())
        val gridWidth = handleWidth + gaps + cellWidth * columns
        return PcGridMetrics(cellWidth, gridWidth, width > 0f && gridWidth > width)
    }
}
