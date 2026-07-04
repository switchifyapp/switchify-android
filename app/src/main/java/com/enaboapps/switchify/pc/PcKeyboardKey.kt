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
    Meta("Meta", R.string.pc_key_start),
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

enum class PcKeyboardShortcutKey(val protocolValue: String) {
    Ctrl("Ctrl"),
    Alt("Alt"),
    Shift("Shift"),
    Meta("Meta"),
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    E("E"),
    F("F"),
    G("G"),
    H("H"),
    I("I"),
    J("J"),
    K("K"),
    L("L"),
    M("M"),
    N("N"),
    O("O"),
    P("P"),
    Q("Q"),
    R("R"),
    S("S"),
    T("T"),
    U("U"),
    V("V"),
    W("W"),
    X("X"),
    Y("Y"),
    Z("Z")
}

enum class PcKeyboardModifierKey(
    val protocolValue: String,
    @param:StringRes val labelResId: Int
) {
    Ctrl("Ctrl", R.string.pc_modifier_ctrl),
    Alt("Alt", R.string.pc_modifier_alt),
    Shift("Shift", R.string.pc_modifier_shift),
    Meta("Meta", R.string.pc_modifier_start)
}

fun PcKeyboardModifierKey.toShortcutKey(): PcKeyboardShortcutKey {
    return when (this) {
        PcKeyboardModifierKey.Ctrl -> PcKeyboardShortcutKey.Ctrl
        PcKeyboardModifierKey.Alt -> PcKeyboardShortcutKey.Alt
        PcKeyboardModifierKey.Shift -> PcKeyboardShortcutKey.Shift
        PcKeyboardModifierKey.Meta -> PcKeyboardShortcutKey.Meta
    }
}

val PC_SHORTCUT_LETTER_KEYS: List<PcKeyboardShortcutKey> = listOf(
    PcKeyboardShortcutKey.A,
    PcKeyboardShortcutKey.B,
    PcKeyboardShortcutKey.C,
    PcKeyboardShortcutKey.D,
    PcKeyboardShortcutKey.E,
    PcKeyboardShortcutKey.F,
    PcKeyboardShortcutKey.G,
    PcKeyboardShortcutKey.H,
    PcKeyboardShortcutKey.I,
    PcKeyboardShortcutKey.J,
    PcKeyboardShortcutKey.K,
    PcKeyboardShortcutKey.L,
    PcKeyboardShortcutKey.M,
    PcKeyboardShortcutKey.N,
    PcKeyboardShortcutKey.O,
    PcKeyboardShortcutKey.P,
    PcKeyboardShortcutKey.Q,
    PcKeyboardShortcutKey.R,
    PcKeyboardShortcutKey.S,
    PcKeyboardShortcutKey.T,
    PcKeyboardShortcutKey.U,
    PcKeyboardShortcutKey.V,
    PcKeyboardShortcutKey.W,
    PcKeyboardShortcutKey.X,
    PcKeyboardShortcutKey.Y,
    PcKeyboardShortcutKey.Z
)
