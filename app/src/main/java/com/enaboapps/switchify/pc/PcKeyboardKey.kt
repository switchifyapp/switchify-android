package com.enaboapps.switchify.pc

import androidx.annotation.StringRes
import com.enaboapps.switchify.R

enum class PcKeyboardKey(
    val protocolValue: String,
    @param:StringRes val labelResId: Int
) {
    Backspace("Backspace", R.string.pc_key_backspace),
    Delete("Delete", R.string.pc_key_delete),
    Enter("Enter", R.string.pc_key_enter),
    Escape("Escape", R.string.pc_key_escape),
    Space("Space", R.string.pc_key_space),
    Tab("Tab", R.string.pc_key_tab),
    ArrowUp("ArrowUp", R.string.pc_key_arrow_up),
    ArrowDown("ArrowDown", R.string.pc_key_arrow_down),
    ArrowLeft("ArrowLeft", R.string.pc_key_arrow_left),
    ArrowRight("ArrowRight", R.string.pc_key_arrow_right),
    Home("Home", R.string.pc_key_home),
    End("End", R.string.pc_key_end),
    PageUp("PageUp", R.string.pc_key_page_up),
    PageDown("PageDown", R.string.pc_key_page_down)
}
