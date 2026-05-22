package com.enaboapps.switchify.service.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs an [AiTask] on the best available on-device backend: AICore (Gemini
 * Nano) when the device supports it, otherwise MediaPipe with the downloaded
 * Gemma model. Backend selection lives here so individual features never
 * repeat it — a new AI feature is just an [AiTask] plus a call to [run].
 */
object OnDeviceAi {
    private const val TAG = "OnDeviceAi"
    private const val MAX_IMAGE_DIMENSION = 1024

    // Ordered by preference; the first backend that is READY runs the task.
    private val backends: List<AiBackend> = listOf(AiCoreBackend, MediaPipeBackend)

    /** The best availability across all backends. */
    suspend fun availability(context: Context): AiAvailability {
        var best = AiAvailability.UNAVAILABLE
        for (backend in backends) {
            when (backend.availability(context)) {
                AiAvailability.READY -> return AiAvailability.READY
                AiAvailability.NEEDS_SETUP -> best = AiAvailability.NEEDS_SETUP
                AiAvailability.UNAVAILABLE -> {}
            }
        }
        return best
    }

    /** Run [task], returning its parsed result or a typed failure. */
    suspend fun <T> run(
        context: Context,
        task: AiTask<T>,
        image: Bitmap? = null
    ): AiResult<T> = withContext(Dispatchers.Default) {
        val backend = backends.firstOrNull {
            it.availability(context) == AiAvailability.READY
        } ?: return@withContext AiResult.Failure(AiFailure.NOT_READY)

        try {
            val scaled = image?.let { downscale(it) }
            val raw = backend.generate(context, task.prompt, scaled)
            AiResult.Success(task.parse(raw))
        } catch (e: Exception) {
            Log.e(TAG, "On-device AI task failed", e)
            AiResult.Failure(AiFailure.INFERENCE_ERROR)
        }
    }

    // Cap the longest edge so a full-resolution screenshot doesn't blow past
    // what the on-device vision models accept.
    private fun downscale(bitmap: Bitmap): Bitmap {
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        if (longestEdge <= MAX_IMAGE_DIMENSION) return bitmap
        val scale = MAX_IMAGE_DIMENSION.toFloat() / longestEdge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
