package com.enaboapps.switchify.pc.remote.layouts

import androidx.annotation.StringRes
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.remote.actions.PcActionCategory

data class PcActionOption(
    val id: String,
    val name: String,
    val category: String,
    val keywords: List<String>,
    val explanation: String? = null
)

data class PcActionOptionGroup(val category: String, val options: List<PcActionOption>)

object PcActionPickerModel {
    private val whitespace = Regex("\\s+")

    fun search(options: List<PcActionOption>, query: String): List<PcActionOption> {
        val words = query.trim().lowercase().split(whitespace).filter { it.isNotEmpty() }
        return options.filter { option ->
            val text = (listOf(option.name, option.category) + option.keywords).joinToString(" ").lowercase()
            words.all { text.contains(it) }
        }
    }

    fun group(options: List<PcActionOption>): List<PcActionOptionGroup> =
        options.groupBy { it.category }.map { (category, grouped) -> PcActionOptionGroup(category, grouped) }

    @StringRes
    fun categoryLabel(category: PcActionCategory): Int = when (category) {
        PcActionCategory.PointerMovement -> R.string.pc_category_pointer_movement
        PcActionCategory.MouseButtons -> R.string.pc_category_mouse_buttons
        PcActionCategory.Scrolling -> R.string.pc_category_scrolling
        PcActionCategory.PointerSpeed -> R.string.pc_category_pointer_speed
        PcActionCategory.Monitors -> R.string.pc_category_monitors
        PcActionCategory.Modifiers -> R.string.pc_category_modifiers
        PcActionCategory.Windows -> R.string.pc_category_windows
        PcActionCategory.Shortcuts -> R.string.pc_category_shortcuts
        PcActionCategory.PcKeys -> R.string.pc_category_pc_keys
        PcActionCategory.DraftText -> R.string.pc_category_draft_text
    }
}
