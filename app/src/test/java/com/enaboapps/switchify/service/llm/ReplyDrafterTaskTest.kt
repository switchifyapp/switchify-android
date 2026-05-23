package com.enaboapps.switchify.service.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyDrafterTaskTest {

    @Test
    fun `parse splits lines and trims whitespace`() {
        val result = ReplyDrafterTask().parse("  Sounds good \nSee you then\nThanks ")
        assertEquals(listOf("Sounds good", "See you then", "Thanks"), result)
    }

    @Test
    fun `parse strips numbered list markers`() {
        val result = ReplyDrafterTask().parse("1. Yes\n2) No\n3. Maybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parse strips bullet markers`() {
        val result = ReplyDrafterTask().parse("- Yes\n* No\n• Maybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parse drops blank lines`() {
        val result = ReplyDrafterTask().parse("Yes\n\n   \nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parse removes duplicates`() {
        val result = ReplyDrafterTask().parse("Yes\nYes\nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parse caps the result at five suggestions`() {
        val result = ReplyDrafterTask().parse("a\nb\nc\nd\ne\nf\ng")
        assertEquals(listOf("a", "b", "c", "d", "e"), result)
    }

    @Test
    fun `parse returns empty list for blank input`() {
        assertEquals(emptyList<String>(), ReplyDrafterTask().parse("\n   \n"))
    }

    @Test
    fun `parse keeps only the text between the reply tags`() {
        val result = ReplyDrafterTask().parse(
            "Sure, here are some replies:\n<replies>\nYes\nNo\nMaybe\n</replies>\nHope that helps!"
        )
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parse falls back to the whole output when tags are absent`() {
        val result = ReplyDrafterTask().parse("Yes\nNo\nMaybe")
        assertEquals(listOf("Yes", "No", "Maybe"), result)
    }

    @Test
    fun `parse handles a missing closing tag`() {
        val result = ReplyDrafterTask().parse("<replies>\nYes\nNo")
        assertEquals(listOf("Yes", "No"), result)
    }

    @Test
    fun `parse strips wrapping quotes`() {
        val result = ReplyDrafterTask().parse(
            "<replies>\n\"Sounds good\"\n\"See you then\"\n</replies>"
        )
        assertEquals(listOf("Sounds good", "See you then"), result)
    }

    @Test
    fun `first-draft prompt has no refinement block when previousReplies is empty`() {
        val empty = ReplyDrafterTask().prompt
        val blankRefinement = ReplyDrafterTask(refinement = "be more formal").prompt
        assertEquals(
            "Refinement without previous replies must not change the prompt",
            empty,
            blankRefinement
        )
        assertFalse(
            "First-draft prompt must not include the refinement block",
            empty.contains("REFINEMENT")
        )
        assertTrue(
            "First-draft prompt must still contain the replies-open tag",
            empty.contains("<replies>")
        )
    }

    @Test
    fun `refinement block sits between the rules and the format block`() {
        val prompt = ReplyDrafterTask(
            previousReplies = listOf("Sounds good", "Maybe later", "Sorry, can't"),
            refinement = "be more formal"
        ).prompt

        assertTrue(
            "Refinement prompt must include the previous replies",
            prompt.contains("Sounds good") &&
                prompt.contains("Maybe later") &&
                prompt.contains("Sorry, can't")
        )
        assertTrue(
            "Refinement prompt must include the user's direction",
            prompt.contains("be more formal")
        )
        assertTrue(
            "Refinement prompt must explicitly disavow treating the direction as a message",
            prompt.contains("NOT a message in the conversation")
        )

        val rulesIdx = prompt.indexOf("1. Find the other person")
        val refinementIdx = prompt.indexOf("REFINEMENT")
        val formatIdx = prompt.indexOf("Format the answer")
        assertTrue("Rules must come before the refinement block", rulesIdx in 0 until refinementIdx)
        assertTrue("Format block must come after the refinement block", refinementIdx < formatIdx)
    }

    @Test
    fun `refinement with blank direction asks for a meaningfully different spread`() {
        val prompt = ReplyDrafterTask(
            previousReplies = listOf("Yes", "No", "Maybe")
        ).prompt
        assertTrue(
            "Empty-direction refinement must still include the previous replies",
            prompt.contains("Yes") && prompt.contains("No") && prompt.contains("Maybe")
        )
        assertTrue(
            "Empty-direction refinement must ask for a meaningfully different spread",
            prompt.contains("meaningfully different spread")
        )
    }
}
