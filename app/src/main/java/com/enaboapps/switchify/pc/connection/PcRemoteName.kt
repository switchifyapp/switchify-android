package com.enaboapps.switchify.pc.connection

object PcRemoteName {
    const val FALLBACK = "Switchify"
    const val MAX_CHARACTERS = 40

    fun validate(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.codePoints().anyMatch { Character.getType(it) == Character.CONTROL.toInt() }) return null
        if (trimmed.codePointCount(0, trimmed.length) > MAX_CHARACTERS) return null
        return trimmed
    }

    fun deviceModelName(modelName: String?): String = modelName?.let(::validate) ?: FALLBACK

    fun resolve(savedName: String?, modelName: String?): String =
        savedName?.let(::validate) ?: deviceModelName(modelName)
}
