package com.enaboapps.switchify.service.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.core.BaseOptions
import java.io.File

/**
 * Manages on-device LLM inference using MediaPipe
 */
object LlmManager {
    private const val TAG = "LlmManager"
    
    // Default path for the model - can be made configurable later
    private const val DEFAULT_MODEL_PATH = "/sdcard/Download/gemma-3n-E2B-it-int4.task"
    
    private var llmInference: LlmInference? = null
    
    /**
     * Initializes the LLM with the model at the given path
     */
    fun initialize(context: Context, modelPath: String = DEFAULT_MODEL_PATH): Boolean {
        if (llmInference != null) return true
        
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found at $modelPath")
            return false
        }
        
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setMaxTopK(40)
                .setMaxNumImages(1) // Enable vision modality
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "LLM initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            false
        }
    }
    
    /**
     * Generates suggested replies based on a screenshot bitmap
     */
    fun generateReplySuggestions(
        context: Context,
        bitmap: Bitmap,
        prompt: String = "Based on this screenshot of a conversation, suggest 3 short, helpful, and natural-sounding replies. Format as a simple list.",
        onResult: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (llmInference == null) {
            if (!initialize(context)) {
                onError("LLM not initialized and failed to initialize")
                return
            }
        }
        
        val llm = llmInference ?: return
        
        try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.7f)
                .build()
            
            val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
            
            // Add the image to the session
            val image = BitmapImageBuilder(bitmap).build()
            session.addImage(image)
            
            // Add the prompt
            // Gemma 3 turn tokens for better instruction following
            val formattedPrompt = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
            session.addQueryChunk(formattedPrompt)
            
            // Generate response
            val result = session.generateResponse()
            
            // Parse the result into a list of suggestions
            val suggestions = parseSuggestions(result)
            onResult(suggestions)
            
            session.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating replies", e)
            onError("Failed to generate replies: ${e.message}")
        }
    }
    
    /**
     * Simple parser to extract list items from the LLM output
     */
    private fun parseSuggestions(text: String): List<String> {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                // Remove list markers like "1. ", "- ", "* "
                line.replace(Regex("^(\\d+\\.|[-*])\\s*"), "")
            }
            .filter { it.isNotEmpty() }
            .take(5) // Limit to 5 suggestions
    }
    
    /**
     * Cleans up LLM resources
     */
    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
