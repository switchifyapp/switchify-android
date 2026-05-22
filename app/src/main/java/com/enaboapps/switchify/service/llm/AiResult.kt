package com.enaboapps.switchify.service.llm

/** The outcome of running an [AiTask] through [OnDeviceAi]. */
sealed interface AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>
    data class Failure(val reason: AiFailure) : AiResult<Nothing>
}

enum class AiFailure {
    /** No backend is ready — AICore is unsupported and no model is downloaded. */
    NOT_READY,

    /** A backend was ready, but inference failed. */
    INFERENCE_ERROR
}
