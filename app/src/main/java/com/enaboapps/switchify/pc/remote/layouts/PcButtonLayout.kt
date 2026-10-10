package com.enaboapps.switchify.pc.remote.layouts

enum class PcLayoutSurface(val storageKey: String) {
    Mouse("mouse"),
    Typing("typing"),
    Window("window");

    companion object {
        fun fromStorageKey(value: String): PcLayoutSurface? = entries.firstOrNull { it.storageKey == value }
    }
}

enum class PcLayoutAxis {
    Row,
    Column
}

data class PcButtonLayout(val columns: Int, val cells: List<String?>) {
    val rows: Int get() = if (columns > 0) cells.size / columns else 0

    fun row(index: Int): List<String?> = cells.subList(index * columns, (index + 1) * columns)

    companion object {
        const val MAX_ROWS = 20
        const val MAX_COLUMNS = 4
        private val ID_PATTERN = Regex("^[a-zA-Z0-9.-]{1,80}$")

        fun isValid(layout: PcButtonLayout?): Boolean {
            if (layout == null) return false
            val columns = layout.columns
            val cells = layout.cells
            if (columns < 1 || columns > MAX_COLUMNS) return false
            if (cells.isEmpty() || cells.size % columns != 0 || cells.size > columns * MAX_ROWS) return false
            val ids = mutableSetOf<String>()
            return cells.all { id -> id == null || (ID_PATTERN.matches(id) && ids.add(id)) }
        }

        fun initial(ids: List<String>, columns: Int = 3): PcButtonLayout {
            val cells = ids.toMutableList<String?>()
            while (cells.isEmpty() || cells.size % columns != 0) cells += null
            return PcButtonLayout(columns, cells)
        }

        fun moveCell(layout: PcButtonLayout, from: Int, to: Int): PcButtonLayout {
            if (from !in layout.cells.indices || layout.cells[from] == null || from == to || to !in layout.cells.indices) {
                return layout
            }
            val cells = layout.cells.toMutableList()
            cells[from] = layout.cells[to].also { cells[to] = layout.cells[from] }
            return layout.copy(cells = cells)
        }

        fun setCell(layout: PcButtonLayout, index: Int, id: String?): PcButtonLayout {
            if (index !in layout.cells.indices || (id != null && id in layout.cells)) return layout
            return layout.copy(cells = layout.cells.mapIndexed { i, cell -> if (i == index) id else cell })
        }

        fun resize(layout: PcButtonLayout, axis: PcLayoutAxis, index: Int, insert: Boolean): PcButtonLayout {
            val rows = layout.rows
            val count = if (axis == PcLayoutAxis.Row) rows else layout.columns
            val limit = if (axis == PcLayoutAxis.Row) MAX_ROWS else MAX_COLUMNS
            if (index < 0 || index > count - (if (insert) 0 else 1) || (if (insert) count >= limit else count <= 1)) {
                return layout
            }
            val matrix = (0 until rows).map { layout.row(it).toMutableList() }.toMutableList()
            if (axis == PcLayoutAxis.Row) {
                if (insert) matrix.add(index, MutableList(layout.columns) { null }) else matrix.removeAt(index)
            } else {
                matrix.forEach { row -> if (insert) row.add(index, null) else row.removeAt(index) }
            }
            val columns = layout.columns + if (axis == PcLayoutAxis.Column) (if (insert) 1 else -1) else 0
            return PcButtonLayout(columns, matrix.flatten())
        }

        fun moveTrack(layout: PcButtonLayout, axis: PcLayoutAxis, from: Int, to: Int): PcButtonLayout {
            val count = if (axis == PcLayoutAxis.Row) layout.rows else layout.columns
            if (from !in 0 until count || to !in 0 until count || from == to) return layout
            val order = (0 until count).toMutableList()
            order.add(to, order.removeAt(from))
            return PcButtonLayout(
                layout.columns,
                List(layout.cells.size) { i ->
                    val row = i / layout.columns
                    val column = i % layout.columns
                    val sourceRow = if (axis == PcLayoutAxis.Row) order[row] else row
                    val sourceColumn = if (axis == PcLayoutAxis.Column) order[column] else column
                    layout.cells[sourceRow * layout.columns + sourceColumn]
                }
            )
        }
    }
}
