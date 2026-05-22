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

    private val PROMPT = """
        You are helping someone reply in a conversation. The image is a screenshot of
        that conversation. Think through these steps, then write only the reply options.

        1. Find the other person's most recent message — this is what the user is
           replying to, and it matters most. Read the earlier messages only as
           background for the topic, tone, and relationship.
        2. The messages on the right (often a different colour) are the user's own; the
           messages on the left are from the other person. You are drafting what the
           user sends next.
        3. Write 3 to 5 replies that each directly respond to that most recent
           message — in the user's own voice, in the same language as the conversation,
           and ready to send as-is.
        4. Make the replies genuinely different from each other in stance — for example
           one that accepts or agrees, one that is neutral or asks a question, and one
           that disagrees.
        5. Always include at least one reply that lets the user say no, disagree, set a
           boundary, or not commit yet — worded to fit this conversation.
        6. Match the conversation's tone and seriousness. If it is tense, sad, awkward,
           or formal, keep the replies measured and appropriate — do not make them
           cheerful or upbeat.
        7. Notice the conversation's warmth and texting style — kisses (x, xx), emoji,
           pet names, casual abbreviations. If the other person uses them, the replies
           may use them too, including a matching sign-off, so a reply does not come
           across as cold. Keep replies plain when the conversation is plain or formal.
        8. Keep most replies short. Include a longer reply only when a brief message
           would not be enough.

        Output only the replies, one per line. Keep each reply on a single line, even a
        longer one. Do not add numbering, labels, preamble, or any other text.
    """.trimIndent()

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
