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

    @Test
    fun `parseSuggestions keeps only the text between the reply tags`() {
        val result = ReplyDrafterPrompt.parseSuggestions(
            "Sure, here are some replies:\n<replies>\nYes\nNo\nMaybe\n</replies>\nHope that helps!"
        )
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parseSuggestions falls back to the whole output when tags are absent`() {
        val result = ReplyDrafterPrompt.parseSuggestions("Yes\nNo\nMaybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parseSuggestions handles a missing closing tag`() {
        val result = ReplyDrafterPrompt.parseSuggestions("<replies>\nYes\nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parseSuggestions strips wrapping quotes`() {
        val result = ReplyDrafterPrompt.parseSuggestions(
            "<replies>\n\"Sounds good\"\n\"See you then\"\n</replies>"
        )
        assertEquals(listOf("Sounds good", "See you then"), result)
    }
}
