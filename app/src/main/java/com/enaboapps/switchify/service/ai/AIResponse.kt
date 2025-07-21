package com.enaboapps.switchify.service.ai

/**
 * Represents a response from the AI service with success/failure information
 * and optional content or error details.
 */
data class AIResponse(
    val isSuccess: Boolean,
    val content: String? = null,
    val errorMessage: String? = null,
    val responseType: AIResponseType = AIResponseType.TEXT
) {
    companion object {
        fun success(content: String, type: AIResponseType = AIResponseType.TEXT) = 
            AIResponse(isSuccess = true, content = content, responseType = type)
        
        fun error(message: String) = 
            AIResponse(isSuccess = false, errorMessage = message)
        
        fun empty() = 
            AIResponse(isSuccess = true, content = null)
    }
}

/**
 * Types of AI responses supported by the system
 */
enum class AIResponseType {
    TEXT,
    CONTENT_DESCRIPTION,
    CONTEXTUAL_HELP,
    VOICE_COMMAND_RESPONSE,
    APP_SUGGESTION
}