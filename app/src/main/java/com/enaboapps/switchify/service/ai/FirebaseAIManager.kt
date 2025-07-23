package com.enaboapps.switchify.service.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.TextPart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Manages Firebase AI Logic operations for accessibility features.
 * Provides AI-powered content descriptions, contextual help, and intelligent assistance.
 */
class FirebaseAIManager {
    
    companion object {
        private const val TAG = "FirebaseAIManager"
        private const val MODEL_NAME = "gemini-2.5-flash"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_PROMPT_LENGTH = 8000
        
        private const val VISUAL_ANALYSIS_PROMPT = """
            You are an accessibility assistant analyzing a screenshot for users with motor disabilities.
            Analyze this image and identify the most important interactive elements that would help users navigate efficiently.
            Focus on buttons, input fields, links, and other actionable elements.
            Provide a clear description of what you see and rank elements by importance for task completion.
            Keep responses under 200 words.
        """
        
        private const val SCREEN_DESCRIPTION_PROMPT = """
            You are helping a user with visual impairments understand what's on their screen.
            Describe this screenshot clearly and concisely, focusing on:
            - Main content and purpose of the screen
            - Key interactive elements and their locations
            - Important text or information displayed
            - Suggested next actions
            Keep responses under 150 words and be descriptive but concise.
        """
        
        private const val ELEMENT_IDENTIFICATION_PROMPT = """
            Analyze this screenshot and identify all interactive elements (buttons, links, input fields, etc.).
            For each element, provide:
            - Type of element
            - Purpose or label
            - Relative position on screen
            - Importance level (high/medium/low) for accessibility users
            Focus on elements that would be most useful for users with motor disabilities using switch navigation.
        """
    }
    
    private val model by lazy {
        try {
            Firebase.ai(backend = GenerativeBackend.vertexAI())
                .generativeModel(MODEL_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase AI model with Vertex AI backend", e)
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
     * Generates an imaginative switch name based on input type
     * @param inputType The input type (e.g., "Space", "Smile", "Left Wink")
     * @return AIResponse with generated switch name or error
     */
    suspend fun generateSwitchName(inputType: String): AIResponse {
        if (!isAvailable()) {
            return AIResponse.error("Firebase AI is not available")
        }
        
        val prompt = """
            You must respond with ONLY the name, no explanations, no quotes, no other text.
            
            Create a creative, imaginative switch name inspired by: $inputType
            
            Requirements:
            - 2-15 characters only
            - Be creative and fun, not literal
            - Use camelCase like "Zephyr", "Spark", "Dash"
            - Examples: Zephyr, Spark, Flash, Bolt, Swift, Blaze, Nova, Echo
            - Think of powerful, energetic, or elegant names
            - Do not include quotes, periods, or explanations
            
            Return only the name:
        """.trimIndent()
        
        return generateContent(prompt, AIResponseType.TEXT)
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
     * Analyzes a screenshot for accessibility assistance
     * @param bitmap The screenshot to analyze
     * @param additionalContext Optional additional context about the screen
     * @return AIResponse with visual analysis or error
     */
    suspend fun analyzeScreenshot(bitmap: Bitmap, additionalContext: String? = null): AIResponse {
        if (!isAvailable()) {
            return AIResponse.error("Firebase AI is not available")
        }

        val encodedImage = ImageEncoder.createEncodedImage(bitmap)
        if (encodedImage == null) {
            return AIResponse.error("Failed to encode image")
        }

        val prompt = if (additionalContext?.isNotBlank() == true) {
            "$VISUAL_ANALYSIS_PROMPT\n\nAdditional context: $additionalContext"
        } else {
            VISUAL_ANALYSIS_PROMPT
        }

        return generateMultimodalContent(prompt, encodedImage, AIResponseType.VISUAL_ANALYSIS)
    }

    /**
     * Provides detailed screen description for users with visual impairments
     * @param bitmap The screenshot to describe
     * @return AIResponse with screen description or error
     */
    suspend fun describeScreen(bitmap: Bitmap): AIResponse {
        if (!isAvailable()) {
            return AIResponse.error("Firebase AI is not available")
        }

        val encodedImage = ImageEncoder.createEncodedImage(bitmap)
        if (encodedImage == null) {
            return AIResponse.error("Failed to encode image")
        }

        return generateMultimodalContent(SCREEN_DESCRIPTION_PROMPT, encodedImage, AIResponseType.SCREEN_DESCRIPTION)
    }

    /**
     * Identifies interactive elements in a screenshot for switch navigation
     * @param bitmap The screenshot to analyze
     * @return AIResponse with element identification or error
     */
    suspend fun identifyElements(bitmap: Bitmap): AIResponse {
        if (!isAvailable()) {
            return AIResponse.error("Firebase AI is not available")
        }

        val encodedImage = ImageEncoder.createEncodedImage(bitmap)
        if (encodedImage == null) {
            return AIResponse.error("Failed to encode image")
        }

        return generateMultimodalContent(ELEMENT_IDENTIFICATION_PROMPT, encodedImage, AIResponseType.ELEMENT_IDENTIFICATION)
    }

    /**
     * Enhanced contextual help with visual context from screenshot
     * @param bitmap The current screen screenshot
     * @param userQuery Optional specific question from the user
     * @return AIResponse with contextual help including visual analysis
     */
    suspend fun getVisualContextualHelp(bitmap: Bitmap, userQuery: String? = null): AIResponse {
        if (!isAvailable()) {
            return AIResponse.error("Firebase AI is not available")
        }

        val encodedImage = ImageEncoder.createEncodedImage(bitmap)
        if (encodedImage == null) {
            return AIResponse.error("Failed to encode image")
        }

        val prompt = if (userQuery?.isNotBlank() == true) {
            """
            You are an accessibility assistant. The user is asking: "$userQuery"
            
            Analyze the screenshot and provide helpful, actionable advice that answers their question.
            Focus on what they can do with the current screen and how to accomplish their goal.
            Keep responses under 150 words and be encouraging.
            """.trimIndent()
        } else {
            """
            You are an accessibility assistant analyzing this screenshot.
            Provide helpful suggestions for what the user can do next on this screen.
            Focus on the most important actions available and how to access them.
            Keep responses under 150 words and be encouraging.
            """.trimIndent()
        }

        return generateMultimodalContent(prompt, encodedImage, AIResponseType.CONTEXTUAL_HELP)
    }

    /**
     * Validates if a string is valid JSON
     */
    private fun isValidJson(jsonString: String): Boolean {
        return try {
            JSONObject(jsonString)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates AI response based on response type requirements
     */
    private fun validateResponse(text: String, responseType: AIResponseType): String? {
        return when (responseType) {
            AIResponseType.TEXT -> {
                // For node ranking requests, expect JSON format
                if (text.contains("rankings") || text.contains("index")) {
                    if (!isValidJson(text)) {
                        Log.w(TAG, "Expected JSON response but received invalid JSON")
                        return "AI response format validation failed - expected valid JSON"
                    }
                }
                null // Valid
            }
            else -> null // Other types don't require JSON validation
        }
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
                    // Validate response format if required
                    val validationError = validateResponse(text, responseType)
                    if (validationError != null) {
                        Log.e(TAG, "Response validation failed: $validationError")
                        AIResponse.error(validationError)
                    } else {
                        Log.d(TAG, "AI response generated successfully, length: ${text.length}")
                        AIResponse.success(text.trim(), responseType)
                    }
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

    /**
     * Core multimodal content generation method with image support
     */
    private suspend fun generateMultimodalContent(
        prompt: String,
        encodedImage: EncodedImage,
        responseType: AIResponseType
    ): AIResponse {
        val currentModel = model ?: return AIResponse.error("AI model not initialized")

        return try {
            Log.d(TAG, "Generating multimodal content for type: $responseType")

            // Convert base64 back to bitmap for ImagePart constructor
            val decodedBytes = android.util.Base64.decode(encodedImage.base64Data, android.util.Base64.NO_WRAP)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            
            val content = Content(
                parts = listOf(
                    TextPart(prompt),
                    ImagePart(bitmap)
                )
            )

            withTimeout(DEFAULT_TIMEOUT_MS) {
                val response = currentModel.generateContent(content)
                val text = response.text

                if (text.isNullOrBlank()) {
                    Log.w(TAG, "AI returned empty response for multimodal content")
                    AIResponse.error("AI returned empty response")
                } else {
                    // Validate response format if required
                    val validationError = validateResponse(text, responseType)
                    if (validationError != null) {
                        Log.e(TAG, "Multimodal response validation failed: $validationError")
                        AIResponse.error(validationError)
                    } else {
                        Log.d(TAG, "Multimodal AI response generated successfully, length: ${text.length}")
                        AIResponse.success(text.trim(), responseType)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Multimodal AI request timed out", e)
            AIResponse.error("Request timed out. Please try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating multimodal AI content", e)
            AIResponse.error("Failed to generate content: ${e.message}")
        }
    }
}