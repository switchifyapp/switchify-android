package com.enaboapps.switchify.service.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Manages Firebase AI Logic operations for accessibility features.
 * Provides AI-powered content descriptions, contextual help, and intelligent assistance.
 */
class FirebaseAIManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FirebaseAIManager"
        private const val MODEL_NAME = "gemini-2.0-flash"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_PROMPT_LENGTH = 8000
        
        // Prompt templates for different AI tasks
        private const val CONTENT_DESCRIPTION_PROMPT = """
            You are an accessibility assistant helping users with visual impairments. 
            Provide a clear, concise description of the following UI content or screen element.
            Focus on what's most important for navigation and understanding.
            Keep responses under 150 words.
            
            Content: %s
        """
        
        private const val CONTEXTUAL_HELP_PROMPT = """
            You are an accessibility assistant. A user is currently on this screen/context: %s
            Provide helpful, actionable suggestions for what they can do next.
            Focus on accessibility features and common tasks.
            Keep responses under 100 words and be encouraging.
            
            Current context: %s
        """
        
        private const val VOICE_COMMAND_PROMPT = """
            You are processing a voice command for an accessibility app. 
            Parse this command and provide a structured response about what action should be taken.
            Respond with: ACTION_TYPE: [tap|swipe|navigate|help|unknown] | TARGET: [specific element if applicable] | CONFIDENCE: [high|medium|low]
            
            Voice command: %s
        """
    }
    
    private val model by lazy {
        try {
            Firebase.ai.generativeModel(MODEL_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase AI model", e)
            null
        }
    }
    
    /**
     * Check if Firebase AI is available and properly configured
     */
    fun isAvailable(): Boolean {
        return model != null
    }
    
    /**
     * Generates accessible content descriptions for UI elements or screen content
     * @param content The content to describe
     * @return AIResponse with generated description or error
     */
    suspend fun generateContentDescription(content: String): AIResponse {
        if (!isAvailable()) {
            return AIResponse.error("Firebase AI is not available")
        }
        
        if (content.isBlank()) {
            return AIResponse.error("Content cannot be empty")
        }
        
        val trimmedContent = content.take(MAX_PROMPT_LENGTH)
        val prompt = CONTENT_DESCRIPTION_PROMPT.format(trimmedContent)
        
        return generateContent(prompt, AIResponseType.CONTENT_DESCRIPTION)
    }
    
    /**
     * Provides contextual help based on current screen or app state
     * @param screenContent Description of current screen/context
     * @return AIResponse with contextual suggestions or error
     */
    suspend fun getContextualHelp(screenContent: String): AIResponse {
        if (!isAvailable()) {
            return AIResponse.error("Firebase AI is not available")
        }
        
        if (screenContent.isBlank()) {
            return AIResponse.error("Screen content cannot be empty")
        }
        
        val trimmedContent = screenContent.take(MAX_PROMPT_LENGTH)
        val prompt = CONTEXTUAL_HELP_PROMPT.format(trimmedContent, trimmedContent)
        
        return generateContent(prompt, AIResponseType.CONTEXTUAL_HELP)
    }
    
    /**
     * Processes voice commands and provides structured responses
     * @param command The voice command to process
     * @return AIResponse with command interpretation or error
     */
    suspend fun processVoiceCommand(command: String): AIResponse {
        if (!isAvailable()) {
            return AIResponse.error("Firebase AI is not available")
        }
        
        if (command.isBlank()) {
            return AIResponse.error("Voice command cannot be empty")
        }
        
        val trimmedCommand = command.take(MAX_PROMPT_LENGTH)
        val prompt = VOICE_COMMAND_PROMPT.format(trimmedCommand)
        
        return generateContent(prompt, AIResponseType.VOICE_COMMAND_RESPONSE)
    }
    
    /**
     * Generates general text content using AI
     * @param prompt The prompt to send to the AI
     * @return AIResponse with generated content or error
     */
    suspend fun generateText(prompt: String): AIResponse {
        if (!isAvailable()) {
            return AIResponse.error("Firebase AI is not available")
        }
        
        if (prompt.isBlank()) {
            return AIResponse.error("Prompt cannot be empty")
        }
        
        val trimmedPrompt = prompt.take(MAX_PROMPT_LENGTH)
        return generateContent(trimmedPrompt, AIResponseType.TEXT)
    }
    
    /**
     * Core content generation method with error handling and timeout
     */
    private suspend fun generateContent(prompt: String, responseType: AIResponseType): AIResponse {
        val currentModel = model ?: return AIResponse.error("AI model not initialized")
        
        return try {
            Log.d(TAG, "Generating content for type: $responseType")
            
            withTimeout(DEFAULT_TIMEOUT_MS) {
                val response = currentModel.generateContent(prompt)
                val text = response.text
                
                if (text.isNullOrBlank()) {
                    Log.w(TAG, "AI returned empty response")
                    AIResponse.error("AI returned empty response")
                } else {
                    Log.d(TAG, "AI response generated successfully, length: ${text.length}")
                    AIResponse.success(text.trim(), responseType)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI request timed out", e)
            AIResponse.error("Request timed out. Please try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI content", e)
            AIResponse.error("Failed to generate content: ${e.message}")
        }
    }
}