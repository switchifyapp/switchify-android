package com.enaboapps.switchify.service.llm

/**
 * One on-device AI feature: the [prompt] sent to the model, and [parse] which
 * turns the raw model text into the feature's result type. Adding an AI
 * feature means writing one [AiTask] and calling [OnDeviceAi.run].
 */
interface AiTask<T> {
    val prompt: String

    fun parse(raw: String): T
}
