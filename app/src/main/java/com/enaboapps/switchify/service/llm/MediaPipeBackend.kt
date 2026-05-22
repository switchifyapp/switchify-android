package com.enaboapps.switchify.service.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.enaboapps.switchify.service.llm.model.ModelManager
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [AiBackend] backed by MediaPipe running the downloaded Gemma model. */
object MediaPipeBackend : AiBackend {
    private const val TAG = "MediaPipeBackend"
    private const val MAX_TOKENS = 1024
    private const val TOP_K = 40
    private const val TEMPERATURE = 0.7f

    private val lock = Any()
    private var llmInference: LlmInference? = null
    private var loadedModelPath: String? = null
    private var generating = false
    private var closeRequested = false

    override suspend fun availability(context: Context): AiAvailability =
        if (ModelManager(context).getModelFileIfReady() != null) {
            AiAvailability.READY
        } else {
            AiAvailability.NEEDS_SETUP
        }

    override suspend fun generate(context: Context, prompt: String, image: Bitmap?): String =
        withContext(Dispatchers.Default) {
            val modelPath = ModelManager(context).getModelFileIfReady()?.absolutePath
                ?: throw IllegalStateException("Model not downloaded")

            val inference: LlmInference
            synchronized(lock) {
                inference = initialize(context, modelPath)
                generating = true
            }

            var session: LlmInferenceSession? = null
            try {
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(TOP_K)
                    .setTemperature(TEMPERATURE)
                    .setGraphOptions(
                        GraphOptions.builder().setEnableVisionModality(image != null).build()
                    )
                    .build()
                session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
                session.addQueryChunk(prompt)
                if (image != null) {
                    session.addImage(BitmapImageBuilder(image).build())
                }
                session.generateResponse()
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

    /** Frees the native model. Deferred until the current run finishes. */
    fun close() {
        synchronized(lock) {
            if (generating) {
                closeRequested = true
            } else {
                releaseInference()
            }
        }
    }

    // Returns the cached inference, reloading it if the model path changed.
    // Caller must hold [lock]; throws if the native model cannot be loaded.
    private fun initialize(context: Context, modelPath: String): LlmInference {
        val existing = llmInference
        if (existing != null && loadedModelPath == modelPath) return existing
        releaseInference()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .setMaxNumImages(1)
            .build()
        val inference = LlmInference.createFromOptions(context, options)
        llmInference = inference
        loadedModelPath = modelPath
        return inference
    }

    /** Frees the native model. Caller must hold [lock]. */
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
