package com.enaboapps.switchify.pc

const val PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH = 2_000

fun isSafePcTypedText(text: String): Boolean {
    if (text.length > PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH) return false
    return text.none { char ->
        val code = char.code
        (code in 0x00..0x1f && char != '\t' && char != '\n' && char != '\r') ||
                code in 0x7f..0x9f
    }
}
