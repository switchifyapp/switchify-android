package com.enaboapps.switchify.service.llm

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

object AiCoreManager {
    private const val TAG = "AiCoreManager"
    private const val TOP_K = 40
    private const val TEMPERATURE = 0.7f

    enum class Availability { AVAILABLE, DOWNLOADABLE, UNAVAILABLE }

    private val client by lazy { Generation.getClient() }

    suspend fun availability(): Availability =
        try {
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> Availability.AVAILABLE
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> Availability.DOWNLOADABLE
                else -> Availability.UNAVAILABLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "AICore status check failed", e)
            Availability.UNAVAILABLE
        }

    suspend fun generateReplySuggestions(bitmap: Bitmap): List<String> {
        val response = client.generateContent(
            generateContentRequest(
                ImagePart(ReplyDrafterPrompt.downscale(bitmap)),
                TextPart(ReplyDrafterPrompt.PROMPT)
            ) {
                temperature = TEMPERATURE
                topK = TOP_K
            }
        )
        val text = response.candidates.firstOrNull()?.text.orEmpty()
        return ReplyDrafterPrompt.parseSuggestions(text)
    }

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
