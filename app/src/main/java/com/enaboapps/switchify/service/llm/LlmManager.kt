package com.enaboapps.switchify.service.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

object LlmManager {
    private const val TAG = "LlmManager"
    private const val MAX_TOKENS = 1024
    private const val TOP_K = 40
    private const val TEMPERATURE = 0.7f
    private const val MAX_IMAGE_DIMENSION = 1024
    private const val MAX_SUGGESTIONS = 5

    private const val PROMPT =
        "Look at this screenshot of a conversation. Suggest 3 short, natural, " +
            "friendly replies the person could send next. Reply with only the " +
            "suggestions, one per line, with no numbering or extra text."

    private val listMarker = Regex("^(\\d+[.)]|[-*•])\\s*")

    private val lock = Any()
    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null
    private var generating = false
    private var closeRequested = false

    // Caller must hold [lock].
    private fun initialize(context: Context, modelPath: String): Boolean {
        if (llmInference != null && loadedModelPath == modelPath) return true
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model file not found at $modelPath")
            return false
        }
        releaseInference()
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxNumImages(1)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            loadedModelPath = modelPath
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            releaseInference()
            false
        }
    }

    fun generateReplySuggestions(
        context: Context,
        bitmap: Bitmap,
        modelPath: String,
        onResult: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val inference: LlmInference
        synchronized(lock) {
            if (!initialize(context, modelPath)) {
                onError("Could not load the language model")
                return
            }
            inference = llmInference ?: run {
                onError("Could not load the language model")
                return
            }
            generating = true
        }

        var session: LlmInferenceSession? = null
        try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .setGraphOptions(
                    GraphOptions.builder().setEnableVisionModality(true).build()
                )
                .build()
            session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            session.addQueryChunk(PROMPT)
            session.addImage(BitmapImageBuilder(downscale(bitmap)).build())
            onResult(parseSuggestions(session.generateResponse()))
        } catch (e: Exception) {
            Log.e(TAG, "Error generating reply suggestions", e)
            onError("Could not generate replies")
        } finally {
            try {
                session?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing session", e)
            }
            synchronized(lock) {
                generating = false
                if (closeRequested) {
                    closeRequested = false
                    releaseInference()
                }
            }
        }
    }

    fun parseSuggestions(text: String): List<String> {
        return text.lines()
            .map { it.trim().replace(listMarker, "").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        if (longestEdge <= MAX_IMAGE_DIMENSION) return bitmap
        val scale = MAX_IMAGE_DIMENSION.toFloat() / longestEdge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    fun close() {
        synchronized(lock) {
            if (generating) {
                closeRequested = true
            } else {
                releaseInference()
            }
        }
    }

    /** Frees the native model. The caller must hold [lock]. */
    private fun releaseInference() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM", e)
        }
        llmInference = null
        loadedModelPath = null
    }
}
