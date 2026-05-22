package com.enaboapps.switchify.service.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyDrafterTaskTest {

    @Test
    fun `parse splits lines and trims whitespace`() {
        val result = ReplyDrafterTask.parse("  Sounds good \nSee you then\nThanks ")
        assertEquals(listOf("Sounds good", "See you then", "Thanks"), result)
    }

    @Test
    fun `parse strips numbered list markers`() {
        val result = ReplyDrafterTask.parse("1. Yes\n2) No\n3. Maybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parse strips bullet markers`() {
        val result = ReplyDrafterTask.parse("- Yes\n* No\n• Maybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parse drops blank lines`() {
        val result = ReplyDrafterTask.parse("Yes\n\n   \nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parse removes duplicates`() {
        val result = ReplyDrafterTask.parse("Yes\nYes\nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parse caps the result at five suggestions`() {
        val result = ReplyDrafterTask.parse("a\nb\nc\nd\ne\nf\ng")
        assertEquals(listOf("a", "b", "c", "d", "e"), result)
    }

    @Test
    fun `parse returns empty list for blank input`() {
        assertEquals(emptyList<String>(), ReplyDrafterTask.parse("\n   \n"))
    }

    @Test
    fun `parse keeps only the text between the reply tags`() {
        val result = ReplyDrafterTask.parse(
            "Sure, here are some replies:\n<replies>\nYes\nNo\nMaybe\n</replies>\nHope that helps!"
        )
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parse falls back to the whole output when tags are absent`() {
        val result = ReplyDrafterTask.parse("Yes\nNo\nMaybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parse handles a missing closing tag`() {
        val result = ReplyDrafterTask.parse("<replies>\nYes\nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parse strips wrapping quotes`() {
        val result = ReplyDrafterTask.parse(
            "<replies>\n\"Sounds good\"\n\"See you then\"\n</replies>"
        )
        assertEquals(listOf("Sounds good", "See you then"), result)
    }
}
