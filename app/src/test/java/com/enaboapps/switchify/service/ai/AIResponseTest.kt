package com.enaboapps.switchify.service.ai

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AIResponse data class and its companion object methods
 */
class AIResponseTest {

    @Test
    fun `success factory method creates valid success response`() {
        val content = "Test content"
        val response = AIResponse.success(content)

        assertTrue(response.isSuccess)
        assertEquals(content, response.content)
        assertNull(response.errorMessage)
        assertEquals(AIResponseType.TEXT, response.responseType)
    }

    @Test
    fun `success factory method with custom type creates response with correct type`() {
        val content = "Content description"
        val response = AIResponse.success(content, AIResponseType.CONTENT_DESCRIPTION)

        assertTrue(response.isSuccess)
        assertEquals(content, response.content)
        assertEquals(AIResponseType.CONTENT_DESCRIPTION, response.responseType)
    }

    @Test
    fun `error factory method creates valid error response`() {
        val errorMessage = "Something went wrong"
        val response = AIResponse.error(errorMessage)

        assertFalse(response.isSuccess)
        assertEquals(errorMessage, response.errorMessage)
        assertNull(response.content)
        assertEquals(AIResponseType.TEXT, response.responseType)
    }

    @Test
    fun `empty factory method creates valid empty response`() {
        val response = AIResponse.empty()

        assertTrue(response.isSuccess)
        assertNull(response.content)
        assertNull(response.errorMessage)
        assertEquals(AIResponseType.TEXT, response.responseType)
    }

    @Test
    fun `AIResponseType enum contains all expected values`() {
        val types = AIResponseType.values()
        
        assertTrue(types.contains(AIResponseType.TEXT))
        assertTrue(types.contains(AIResponseType.CONTENT_DESCRIPTION))
        assertTrue(types.contains(AIResponseType.CONTEXTUAL_HELP))
        assertTrue(types.contains(AIResponseType.VOICE_COMMAND_RESPONSE))
        assertTrue(types.contains(AIResponseType.APP_SUGGESTION))
        assertEquals(5, types.size)
    }

    @Test
    fun `AIResponse data class properties work correctly`() {
        val response = AIResponse(
            isSuccess = true,
            content = "Test content",
            errorMessage = null,
            responseType = AIResponseType.CONTEXTUAL_HELP
        )

        assertTrue(response.isSuccess)
        assertEquals("Test content", response.content)
        assertNull(response.errorMessage)
        assertEquals(AIResponseType.CONTEXTUAL_HELP, response.responseType)
    }

    @Test
    fun `AIResponse with both content and error message`() {
        val response = AIResponse(
            isSuccess = false,
            content = "Partial content",
            errorMessage = "Error occurred",
            responseType = AIResponseType.TEXT
        )

        assertFalse(response.isSuccess)
        assertEquals("Partial content", response.content)
        assertEquals("Error occurred", response.errorMessage)
    }
}