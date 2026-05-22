package com.enaboapps.switchify.service.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyDrafterPromptTest {

    @Test
    fun `parseSuggestions splits lines and trims whitespace`() {
        val result = ReplyDrafterPrompt.parseSuggestions("  Sounds good \nSee you then\nThanks ")
        assertEquals(listOf("Sounds good", "See you then", "Thanks"), result)
    }

    @Test
    fun `parseSuggestions strips numbered list markers`() {
        val result = ReplyDrafterPrompt.parseSuggestions("1. Yes\n2) No\n3. Maybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parseSuggestions strips bullet markers`() {
        val result = ReplyDrafterPrompt.parseSuggestions("- Yes\n* No\n• Maybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parseSuggestions drops blank lines`() {
        val result = ReplyDrafterPrompt.parseSuggestions("Yes\n\n   \nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parseSuggestions removes duplicates`() {
        val result = ReplyDrafterPrompt.parseSuggestions("Yes\nYes\nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parseSuggestions caps the result at five suggestions`() {
        val result = ReplyDrafterPrompt.parseSuggestions("a\nb\nc\nd\ne\nf\ng")
        assertEquals(listOf("a", "b", "c", "d", "e"), result)
    }

    @Test
    fun `parseSuggestions returns empty list for blank input`() {
        assertEquals(emptyList<String>(), ReplyDrafterPrompt.parseSuggestions("\n   \n"))
    }
}
