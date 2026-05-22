package com.enaboapps.switchify.service.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

/** [AiBackend] backed by AICore / Gemini Nano via the ML Kit GenAI Prompt API. */
object AiCoreBackend : AiBackend {
    private const val TAG = "AiCoreBackend"
    private const val TOP_K = 40
    private const val TEMPERATURE = 0.7f

    private val client by lazy { Generation.getClient() }

    override suspend fun availability(context: Context): AiAvailability =
        try {
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> AiAvailability.READY
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                    AiAvailability.NEEDS_SETUP

                else -> AiAvailability.UNAVAILABLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "AICore status check failed", e)
            AiAvailability.UNAVAILABLE
        }

    override suspend fun generate(context: Context, prompt: String, image: Bitmap?): String {
        val request = if (image != null) {
            generateContentRequest(ImagePart(image), TextPart(prompt)) {
                temperature = TEMPERATURE
                topK = TOP_K
            }
        } else {
            generateContentRequest(TextPart(prompt)) {
                temperature = TEMPERATURE
                topK = TOP_K
            }
        }
        val response = client.generateContent(request)
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    /**
     * Trigger AICore's own (small, OS-managed) model download, returning true
     * once the feature is ready. AICore-specific — not part of [AiBackend].
     */
    suspend fun prepare(): Boolean =
        try {
            var completed = false
            client.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadCompleted -> completed = true
                    is DownloadStatus.DownloadFailed ->
                        Log.e(TAG, "AICore model download failed")

                    else -> {}
                }
            }
            completed
        } catch (e: Exception) {
            Log.e(TAG, "AICore prepare failed", e)
            false
        }
}
