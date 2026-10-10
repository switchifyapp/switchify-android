package com.enaboapps.switchify.pc.protocol

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
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
            if (value % 1.0 == 0.0 && abs(value) < LONG_RANGE_LIMIT) {
                builder.append(value.toLong())
            } else {
                builder.append(shortestDecimal(value))
            }
        }

        private fun shortestDecimal(value: Double): String {
            val decimal = BigDecimal(abs(value).toString()).round(SIGNIFICANT_DIGITS).stripTrailingZeros()
            val digits = decimal.unscaledValue().toString()
            val exponent = -decimal.scale()
            val pointPosition = digits.length + exponent
            val body = when {
                exponent >= 0 && pointPosition <= MAX_PLAIN_DIGITS -> digits + "0".repeat(exponent) + ".0"
                pointPosition in 1..MAX_PLAIN_DIGITS ->
                    digits.substring(0, pointPosition) + "." + digits.substring(pointPosition)
                pointPosition in MIN_PLAIN_POINT_POSITION..0 -> "0." + "0".repeat(-pointPosition) + digits
                digits.length == 1 -> digits + exponentSuffix(pointPosition - 1)
                else -> digits.first() + "." + digits.substring(1) + exponentSuffix(pointPosition - 1)
            }
            return if (value < 0) "-$body" else body
        }

        private fun exponentSuffix(exponent: Int): String = if (exponent < 0) "e$exponent" else "e+$exponent"

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

    private const val LONG_RANGE_LIMIT = 9.223372036854775807E18
    private const val MAX_PLAIN_DIGITS = 16
    private const val MIN_PLAIN_POINT_POSITION = -4
    private val SIGNIFICANT_DIGITS = MathContext(15, RoundingMode.HALF_EVEN)
}
