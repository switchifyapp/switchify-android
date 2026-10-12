package com.enaboapps.switchify.pc.remote

import androidx.annotation.StringRes

sealed class PcText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : PcText()
    data class Literal(val text: String) : PcText()
    data class Joined(val parts: List<PcText>, val separator: String) : PcText()
}

object PcTextLimit {
    const val MAX_UTF16_UNITS = 2_000

    fun limit(text: String, max: Int = MAX_UTF16_UNITS): String {
        if (text.length <= max) return text
        val end = if (Character.isHighSurrogate(text[max - 1])) max - 1 else max
        return text.substring(0, end)
    }
}
