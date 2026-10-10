package com.enaboapps.switchify.pc.remote

import androidx.annotation.StringRes

sealed class PcText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : PcText()
    data class Literal(val text: String) : PcText()
}
