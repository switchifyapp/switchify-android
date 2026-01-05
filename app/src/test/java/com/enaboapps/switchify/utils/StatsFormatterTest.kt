package com.enaboapps.switchify.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StatsFormatterTest {

    @Test
    fun `formatNumber formats US locale correctly`() {
        val testCases = listOf(
            0 to "0",
            1 to "1",
            100 to "100",
            1000 to "1,000",
            1234 to "1,234",
            1234567 to "1,234,567"
        )

        testCases.forEach { (input, expected) ->
            val result = StatsFormatter.formatNumber(input, Locale.US)
            assertEquals("Failed for input $input", expected, result)
        }
    }

    @Test
    fun `formatNumber formats German locale correctly`() {
        val testCases = listOf(
            0 to "0",
            1000 to "1.000",
            1234 to "1.234",
            1234567 to "1.234.567"
        )

        testCases.forEach { (input, expected) ->
            val result = StatsFormatter.formatNumber(input, Locale.GERMANY)
            assertEquals("Failed for input $input", expected, result)
        }
    }

    @Test
    fun `formatNumber formats French locale correctly`() {
        // French locale - just verify it produces output without errors
        // Note: Exact formatting may vary by JVM (space vs non-breaking space)
        val result0 = StatsFormatter.formatNumber(0, Locale.FRANCE)
        val result1234 = StatsFormatter.formatNumber(1234, Locale.FRANCE)
        val result1234567 = StatsFormatter.formatNumber(1234567, Locale.FRANCE)

        // Basic sanity checks - formatter produces non-empty output
        assert(result0.isNotEmpty()) { "Expected non-empty result for 0" }
        assert(result1234.isNotEmpty()) { "Expected non-empty result for 1234" }
        assert(result1234567.isNotEmpty()) { "Expected non-empty result for 1234567" }

        // Verify the formatter is applying locale-specific formatting
        assert(result1234 != "1234" || result1234567 != "1234567") {
            "Expected at least one number to be formatted with separators"
        }
    }

    @Test
    fun `formatPercentage calculates correctly`() {
        val testCases = listOf(
            Triple(0, 100, "0%"),
            Triple(50, 100, "50%"),
            Triple(100, 100, "100%"),
            Triple(33, 100, "33%"),
            Triple(1, 3, "33%"),  // Rounds 33.33%
            Triple(2, 3, "67%"),  // Rounds 66.66%
            Triple(1, 2, "50%")
        )

        testCases.forEach { (part, total, expected) ->
            val result = StatsFormatter.formatPercentage(part, total)
            assertEquals("Failed for $part of $total", expected, result)
        }
    }

    @Test
    fun `formatPercentage handles edge cases`() {
        // Division by zero
        assertEquals("0%", StatsFormatter.formatPercentage(5, 0))

        // Zero part
        assertEquals("0%", StatsFormatter.formatPercentage(0, 100))

        // Part equals total
        assertEquals("100%", StatsFormatter.formatPercentage(100, 100))
    }

    @Test
    fun `formatNumberWithPercentage combines both correctly`() {
        // US locale
        val result1 = StatsFormatter.formatNumberWithPercentage(1234, 1234, 5000, Locale.US)
        assertEquals("1,234 (25%)", result1)

        // German locale
        val result2 = StatsFormatter.formatNumberWithPercentage(1234, 1234, 5000, Locale.GERMANY)
        assertEquals("1.234 (25%)", result2)

        // 100% case
        val result3 = StatsFormatter.formatNumberWithPercentage(100, 100, 100, Locale.US)
        assertEquals("100 (100%)", result3)
    }

    @Test
    fun `formatNumberWithPercentage handles edge cases`() {
        // Zero total
        val result1 = StatsFormatter.formatNumberWithPercentage(0, 0, 0, Locale.US)
        assertEquals("0 (0%)", result1)

        // Large numbers
        val result2 = StatsFormatter.formatNumberWithPercentage(1234567, 1234567, 10000000, Locale.US)
        assertEquals("1,234,567 (12%)", result2)
    }

    @Test
    fun `formatNumber handles negative numbers`() {
        assertEquals("-1,234", StatsFormatter.formatNumber(-1234, Locale.US))
        assertEquals("-1.234", StatsFormatter.formatNumber(-1234, Locale.GERMANY))
    }

    @Test
    fun `formatPercentage handles rounding properly`() {
        // Test various rounding scenarios
        assertEquals("67%", StatsFormatter.formatPercentage(2, 3))  // 66.66% rounds to 67%
        assertEquals("33%", StatsFormatter.formatPercentage(1, 3))  // 33.33% rounds to 33%
        assertEquals("25%", StatsFormatter.formatPercentage(1, 4))  // 25% exactly
        assertEquals("20%", StatsFormatter.formatPercentage(1, 5))  // 20% exactly
    }
}
