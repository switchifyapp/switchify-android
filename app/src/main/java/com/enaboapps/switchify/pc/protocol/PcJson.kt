package com.enaboapps.switchify.pc.protocol

import kotlin.math.abs

typealias PcJsonObject = Map<String, Any?>

object PcJson {
    fun stringify(value: Any?, sortKeys: Boolean = false, escapeSlashes: Boolean = false): String =
        StringBuilder().also { Writer(it, sortKeys, escapeSlashes).append(value) }.toString()

    private class Writer(
        private val builder: StringBuilder,
        private val sortKeys: Boolean,
        private val escapeSlashes: Boolean
    ) {
        fun append(value: Any?) {
            when (value) {
                null -> builder.append("null")
                is String -> appendQuoted(value)
                is Boolean -> builder.append(value)
                is Double -> appendDouble(value)
                is Float -> appendDouble(value.toDouble())
                is Number -> builder.append(value.toLong())
                is Map<*, *> -> appendObject(value)
                is Iterable<*> -> {
                    builder.append('[')
                    value.forEachIndexed { index, item ->
                        if (index > 0) builder.append(',')
                        append(item)
                    }
                    builder.append(']')
                }
                else -> throw IllegalArgumentException("Unsupported JSON value.")
            }
        }

        private fun appendObject(value: Map<*, *>) {
            val keys = value.keys.map { key ->
                require(key is String) { "JSON object keys must be strings." }
                key
            }
            builder.append('{')
            (if (sortKeys) keys.sorted() else keys).forEachIndexed { index, key ->
                if (index > 0) builder.append(',')
                appendQuoted(key)
                builder.append(':')
                append(value[key])
            }
            builder.append('}')
        }

        private fun appendDouble(value: Double) {
            require(value.isFinite()) { "JSON numbers must be finite." }
            if (value % 1.0 == 0.0 && abs(value) < MAX_EXACT_WHOLE_DOUBLE) {
                builder.append(value.toLong())
            } else {
                builder.append(value)
            }
        }

        private fun appendQuoted(value: String) {
            builder.append('"')
            var index = 0
            while (index < value.length) {
                val character = value[index]
                when {
                    character == '"' -> builder.append("\\\"")
                    character == '\\' -> builder.append("\\\\")
                    character == '/' && escapeSlashes -> builder.append("\\/")
                    character == '\b' -> builder.append("\\b")
                    character == '\u000C' -> builder.append("\\f")
                    character == '\n' -> builder.append("\\n")
                    character == '\r' -> builder.append("\\r")
                    character == '\t' -> builder.append("\\t")
                    character < ' ' -> appendUnicodeEscape(character)
                    character.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate() -> {
                        builder.append(character).append(value[index + 1])
                        index += 1
                    }
                    character.isSurrogate() -> appendUnicodeEscape(character)
                    else -> builder.append(character)
                }
                index += 1
            }
            builder.append('"')
        }

        private fun appendUnicodeEscape(character: Char) {
            builder.append("\\u").append(character.code.toString(16).padStart(4, '0'))
        }
    }

    private const val MAX_EXACT_WHOLE_DOUBLE = 9_007_199_254_740_992.0
}
