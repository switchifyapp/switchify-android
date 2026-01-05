package com.enaboapps.switchify.utils

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utility class for formatting statistics numbers and percentages with locale support.
 */
class StatsFormatter {
    companion object {
        /**
         * Formats an integer using the device's locale.
         *
         * Examples:
         * - US locale: 1234 -> "1,234"
         * - German locale: 1234 -> "1.234"
         * - French locale: 1234 -> "1 234"
         *
         * @param value The integer value to format
         * @param locale The locale to use for formatting (defaults to device locale)
         * @return Formatted number string
         */
        fun formatNumber(value: Int, locale: Locale = Locale.getDefault()): String {
            val formatter = NumberFormat.getNumberInstance(locale)
            return formatter.format(value)
        }

        /**
         * Calculates and formats a percentage.
         *
         * Examples:
         * - 45 of 100 -> "45%"
         * - 33 of 99 -> "33%"
         * - 0 of 100 -> "0%"
         * - Edge case: 5 of 0 -> "0%"
         *
         * @param part The partial count
         * @param total The total count
         * @param locale The locale to use for formatting (defaults to device locale)
         * @return Formatted percentage string
         */
        fun formatPercentage(part: Int, total: Int, locale: Locale = Locale.getDefault()): String {
            if (total == 0) return "0%"
            val percentage = (part.toDouble() / total.toDouble() * 100).roundToInt()
            return "$percentage%"
        }

        /**
         * Formats a number with its percentage in parentheses.
         *
         * Examples:
         * - formatNumberWithPercentage(1234, 45, 100) -> "1,234 (45%)"
         * - formatNumberWithPercentage(5000, 50, 100, Locale.GERMANY) -> "5.000 (50%)"
         *
         * @param value The value to format
         * @param part The partial count for percentage calculation
         * @param total The total count for percentage calculation
         * @param locale The locale to use for formatting (defaults to device locale)
         * @return Formatted string with number and percentage
         */
        fun formatNumberWithPercentage(
            value: Int,
            part: Int,
            total: Int,
            locale: Locale = Locale.getDefault()
        ): String {
            val formattedNumber = formatNumber(value, locale)
            val formattedPercentage = formatPercentage(part, total, locale)
            return "$formattedNumber ($formattedPercentage)"
        }
    }
}
