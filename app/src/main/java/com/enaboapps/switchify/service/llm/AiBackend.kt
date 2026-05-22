package com.enaboapps.switchify.service.llm

import android.content.Context
import android.graphics.Bitmap

/** Whether on-device AI can run right now. */
enum class AiAvailability {
    /** Ready to run inference immediately. */
    READY,

    /** Supported, but a one-time setup (model download) is needed first. */
    NEEDS_SETUP,

    /** Not supported on this device. */
    UNAVAILABLE
}

/**
 * A generic on-device text/vision model. A backend knows nothing about any
 * specific feature — it takes a prompt (and optional image) and returns the
 * raw model text. [OnDeviceAi] chooses a backend and runs an [AiTask] on it.
 */
interface AiBackend {
    suspend fun availability(context: Context): AiAvailability

    /** Run inference and return the raw model output. */
    suspend fun generate(context: Context, prompt: String, image: Bitmap?): String
}
