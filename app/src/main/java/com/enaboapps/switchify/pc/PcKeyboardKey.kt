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
    PageDown("PageDown", R.string.pc_key_page_down),
    F1("F1", R.string.pc_key_f1),
    F2("F2", R.string.pc_key_f2),
    F3("F3", R.string.pc_key_f3),
    F4("F4", R.string.pc_key_f4),
    F5("F5", R.string.pc_key_f5),
    F6("F6", R.string.pc_key_f6),
    F7("F7", R.string.pc_key_f7),
    F8("F8", R.string.pc_key_f8),
    F9("F9", R.string.pc_key_f9),
    F10("F10", R.string.pc_key_f10),
    F11("F11", R.string.pc_key_f11),
    F12("F12", R.string.pc_key_f12)
}
