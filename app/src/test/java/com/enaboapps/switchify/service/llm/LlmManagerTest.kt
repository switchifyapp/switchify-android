package com.enaboapps.switchify.service.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmManagerTest {

    @Test
    fun `parseSuggestions splits lines and trims whitespace`() {
        val result = LlmManager.parseSuggestions("  Sounds good \nSee you then\nThanks ")
        assertEquals(listOf("Sounds good", "See you then", "Thanks"), result)
    }

    @Test
    fun `parseSuggestions strips numbered list markers`() {
        val result = LlmManager.parseSuggestions("1. Yes\n2) No\n3. Maybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parseSuggestions strips bullet markers`() {
        val result = LlmManager.parseSuggestions("- Yes\n* No\n• Maybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parseSuggestions drops blank lines`() {
        val result = LlmManager.parseSuggestions("Yes\n\n   \nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parseSuggestions removes duplicates`() {
        val result = LlmManager.parseSuggestions("Yes\nYes\nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parseSuggestions caps the result at five suggestions`() {
        val result = LlmManager.parseSuggestions("a\nb\nc\nd\ne\nf\ng")
        assertEquals(listOf("a", "b", "c", "d", "e"), result)
    }

    @Test
    fun `parseSuggestions returns empty list for blank input`() {
        assertEquals(emptyList<String>(), LlmManager.parseSuggestions("\n   \n"))
    }
}
