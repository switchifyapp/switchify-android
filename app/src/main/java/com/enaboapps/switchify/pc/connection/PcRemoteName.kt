package com.enaboapps.switchify.pc.connection

import com.enaboapps.switchify.pc.control.PcPreferenceStorage

enum class PcRemoteNameError {
    Empty,
    ControlCharacters,
    TooLong
}

object PcRemoteName {
    const val FALLBACK = "Switchify"
    const val MAX_CHARACTERS = 40

    fun error(value: String): PcRemoteNameError? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> PcRemoteNameError.Empty
            trimmed.codePoints().anyMatch { Character.getType(it) == Character.CONTROL.toInt() } -> PcRemoteNameError.ControlCharacters
            trimmed.codePointCount(0, trimmed.length) > MAX_CHARACTERS -> PcRemoteNameError.TooLong
            else -> null
        }
    }

    fun validate(value: String): String? = if (error(value) == null) value.trim() else null

    fun deviceModelName(modelName: String?): String = modelName?.let(::validate) ?: FALLBACK

    fun resolve(savedName: String?, modelName: String?): String =
        savedName?.let(::validate) ?: deviceModelName(modelName)
}

class PcRemoteNameStore(private val storage: PcPreferenceStorage, private val modelName: String?) {
    val automaticName: String get() = PcRemoteName.deviceModelName(modelName)

    fun savedName(): String? = storage.getString(KEY)?.let(PcRemoteName::validate)

    fun resolvedName(): String = PcRemoteName.resolve(storage.getString(KEY), modelName)

    fun save(name: String?) {
        val normalized = name?.let { requireNotNull(PcRemoteName.validate(it)) { "Invalid remote name." } }
        storage.putString(KEY, normalized)
    }

    companion object {
        const val KEY = "remoteName"
    }
}
