package com.enaboapps.switchify.service.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenHighlightsTaskTest {

    @Test
    fun `parse handles a mix of types`() {
        val result = ScreenHighlightsTask.parse(
            """
            <items>
            URL: https://example.com
            PHONE: 555-123-4567
            EMAIL: hello@example.com
            DATE: Tue, 12 Mar at 3pm
            ADDRESS: 1 Infinite Loop, Cupertino
            OTHER: ORDER-99421
            </items>
            """.trimIndent()
        )
        assertEquals(
            listOf(
                ExtractedItem(HighlightType.URL, "https://example.com"),
                ExtractedItem(HighlightType.PHONE, "555-123-4567"),
                ExtractedItem(HighlightType.EMAIL, "hello@example.com"),
                ExtractedItem(HighlightType.DATE, "Tue, 12 Mar at 3pm"),
                ExtractedItem(HighlightType.ADDRESS, "1 Infinite Loop, Cupertino"),
                ExtractedItem(HighlightType.OTHER, "ORDER-99421")
            ),
            result
        )
    }

    @Test
    fun `parse maps unknown TYPE to OTHER`() {
        val result = ScreenHighlightsTask.parse(
            "<items>\nCOUPON: SAVE20\n</items>"
        )
        assertEquals(listOf(ExtractedItem(HighlightType.OTHER, "SAVE20")), result)
    }

    @Test
    fun `parse is case-insensitive on the type label`() {
        val result = ScreenHighlightsTask.parse(
            "<items>\nemail: a@b.com\nphone: 0151 123 4567\n</items>"
        )
        assertEquals(
            listOf(
                ExtractedItem(HighlightType.EMAIL, "a@b.com"),
                ExtractedItem(HighlightType.PHONE, "0151 123 4567")
            ),
            result
        )
    }

    @Test
    fun `parse strips list markers from the type label`() {
        val result = ScreenHighlightsTask.parse(
            "<items>\n1. URL: https://a.example\n- PHONE: 555-0100\n* EMAIL: x@y.z\n</items>"
        )
        assertEquals(
            listOf(
                ExtractedItem(HighlightType.URL, "https://a.example"),
                ExtractedItem(HighlightType.PHONE, "555-0100"),
                ExtractedItem(HighlightType.EMAIL, "x@y.z")
            ),
            result
        )
    }

    @Test
    fun `parse strips wrapping quotes from the value`() {
        val result = ScreenHighlightsTask.parse(
            "<items>\nURL: \"https://quoted.example\"\nOTHER: \"REF-001\"\n</items>"
        )
        assertEquals(
            listOf(
                ExtractedItem(HighlightType.URL, "https://quoted.example"),
                ExtractedItem(HighlightType.OTHER, "REF-001")
            ),
            result
        )
    }

    @Test
    fun `parse dedups by type and value`() {
        val result = ScreenHighlightsTask.parse(
            "<items>\nEMAIL: a@b.com\nEMAIL: a@b.com\nPHONE: a@b.com\n</items>"
        )
        assertEquals(
            listOf(
                ExtractedItem(HighlightType.EMAIL, "a@b.com"),
                ExtractedItem(HighlightType.PHONE, "a@b.com")
            ),
            result
        )
    }

    @Test
    fun `parse caps the result at fifteen items`() {
        val many = (1..20).joinToString("\n") { "OTHER: ref-$it" }
        val result = ScreenHighlightsTask.parse("<items>\n$many\n</items>")
        assertEquals(15, result.size)
        assertEquals(ExtractedItem(HighlightType.OTHER, "ref-1"), result.first())
        assertEquals(ExtractedItem(HighlightType.OTHER, "ref-15"), result.last())
    }

    @Test
    fun `parse skips lines without a colon`() {
        val result = ScreenHighlightsTask.parse(
            "<items>\nEMAIL: a@b.com\nnot an item\nURL: https://ok.example\n</items>"
        )
        assertEquals(
            listOf(
                ExtractedItem(HighlightType.EMAIL, "a@b.com"),
                ExtractedItem(HighlightType.URL, "https://ok.example")
            ),
            result
        )
    }

    @Test
    fun `parse skips lines with an empty value`() {
        val result = ScreenHighlightsTask.parse(
            "<items>\nEMAIL: \nURL: https://ok.example\n</items>"
        )
        assertEquals(
            listOf(ExtractedItem(HighlightType.URL, "https://ok.example")),
            result
        )
    }

    @Test
    fun `parse returns empty when the items block is absent`() {
        val result = ScreenHighlightsTask.parse(
            "Sure, here are some highlights from the screen for you to look at."
        )
        assertEquals(emptyList<ExtractedItem>(), result)
    }

    @Test
    fun `parse returns empty when the items block is empty`() {
        assertEquals(
            emptyList<ExtractedItem>(),
            ScreenHighlightsTask.parse("<items>\n</items>")
        )
    }

    @Test
    fun `parse ignores text outside the items block`() {
        val result = ScreenHighlightsTask.parse(
            "Here you go:\n<items>\nURL: https://x.example\n</items>\nHope that helps!"
        )
        assertEquals(
            listOf(ExtractedItem(HighlightType.URL, "https://x.example")),
            result
        )
    }
}
