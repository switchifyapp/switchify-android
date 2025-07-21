package com.enaboapps.switchify.service.ai

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FirebaseAIManager
 * 
 * Note: These tests focus on the manager's API structure and validation logic.
 * Actual Firebase AI calls require a real Firebase project setup.
 */
class FirebaseAIManagerTest {

    @Test
    fun `FirebaseAIManager class exists and has expected methods`() {
        // This test verifies the manager API structure
        val clazz = FirebaseAIManager::class.java
        assertNotNull(clazz)
        
        // Verify key methods exist
        val methods = clazz.declaredMethods.map { it.name }
        assertTrue("Should have isAvailable method", methods.contains("isAvailable"))
        assertTrue("Should have generateContentDescription method", methods.contains("generateContentDescription"))
        assertTrue("Should have getContextualHelp method", methods.contains("getContextualHelp"))
        assertTrue("Should have processVoiceCommand method", methods.contains("processVoiceCommand"))
        assertTrue("Should have generateText method", methods.contains("generateText"))
    }

    @Test
    fun `FirebaseAIManager constructor requires Context parameter`() {
        val constructors = FirebaseAIManager::class.java.constructors
        assertEquals("Should have one constructor", 1, constructors.size)
        
        val constructor = constructors[0]
        assertEquals("Constructor should take one parameter", 1, constructor.parameterCount)
        assertEquals("Constructor parameter should be Context", "android.content.Context", constructor.parameterTypes[0].name)
    }

    @Test
    fun `AIResponse companion object methods work correctly`() {
        // Test success factory method
        val successResponse = AIResponse.success("test content")
        assertTrue(successResponse.isSuccess)
        assertEquals("test content", successResponse.content)
        assertNull(successResponse.errorMessage)
        assertEquals(AIResponseType.TEXT, successResponse.responseType)

        // Test success with custom type
        val customResponse = AIResponse.success("description", AIResponseType.CONTENT_DESCRIPTION)
        assertTrue(customResponse.isSuccess)
        assertEquals("description", customResponse.content)
        assertEquals(AIResponseType.CONTENT_DESCRIPTION, customResponse.responseType)

        // Test error factory method
        val errorResponse = AIResponse.error("error message")
        assertFalse(errorResponse.isSuccess)
        assertEquals("error message", errorResponse.errorMessage)
        assertNull(errorResponse.content)

        // Test empty factory method
        val emptyResponse = AIResponse.empty()
        assertTrue(emptyResponse.isSuccess)
        assertNull(emptyResponse.content)
        assertNull(emptyResponse.errorMessage)
    }

    @Test
    fun `AIResponseType enum contains all expected values`() {
        val types = AIResponseType.values()
        
        assertTrue(types.contains(AIResponseType.TEXT))
        assertTrue(types.contains(AIResponseType.CONTENT_DESCRIPTION))
        assertTrue(types.contains(AIResponseType.CONTEXTUAL_HELP))
        assertTrue(types.contains(AIResponseType.VOICE_COMMAND_RESPONSE))
        assertTrue(types.contains(AIResponseType.APP_SUGGESTION))
        assertEquals("Should have exactly 5 response types", 5, types.size)
    }

    @Test
    fun `Constants are properly defined`() {
        // Use reflection to verify important constants exist
        val clazz = FirebaseAIManager::class.java
        val companionClass = clazz.declaredClasses.find { it.simpleName == "Companion" }
        assertNotNull("Should have Companion object with constants", companionClass)
    }

    @Test
    fun `FirebaseAIManager has all required public methods`() {
        val clazz = FirebaseAIManager::class.java
        val methods = clazz.declaredMethods.map { it.name }
        
        // Verify all expected methods are present (names only, not signatures due to suspend functions)
        assertTrue("Should have generateContentDescription method", 
            methods.contains("generateContentDescription"))
        assertTrue("Should have getContextualHelp method", 
            methods.contains("getContextualHelp"))
        assertTrue("Should have processVoiceCommand method", 
            methods.contains("processVoiceCommand"))
        assertTrue("Should have generateText method", 
            methods.contains("generateText"))
        assertTrue("Should have isAvailable method", 
            methods.contains("isAvailable"))
        
        // Verify isAvailable method specifically (non-suspend)
        val isAvailableMethod = clazz.getDeclaredMethod("isAvailable")
        assertEquals("isAvailable should return Boolean", "boolean", isAvailableMethod.returnType.name)
    }
}