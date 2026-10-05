package com.enaboapps.switchify.service.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.enaboapps.switchify.service.llm.model.ModelManager
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** [AiBackend] backed by LiteRT-LM running the downloaded Gemma model. */
object LiteRtLmBackend : AiBackend {
    private const val TAG = "LiteRtLmBackend"
    private const val MAX_TOKENS = 1024
    private const val TOP_K = 40
    private const val TOP_P = 0.95
    private const val TEMPERATURE = 0.7

    private val lock = Any()
    private var engine: Engine? = null
    private var loadedModelPath: String? = null
    private var generating = false
    private var closeRequested = false

    override suspend fun availability(context: Context): AiAvailability {
        val modelManager = ModelManager(context)
        modelManager.deleteStaleFiles()
        return if (modelManager.getModelFileIfReady() != null) {
            AiAvailability.READY
        } else {
            AiAvailability.NEEDS_SETUP
        }
    }

    override suspend fun generate(context: Context, prompt: String, image: Bitmap?): String =
        withContext(Dispatchers.Default) {
            val modelPath = ModelManager(context).getModelFileIfReady()?.absolutePath
                ?: throw IllegalStateException("Model not downloaded")

            val loadedEngine: Engine
            synchronized(lock) {
                loadedEngine = initialize(context, modelPath)
                generating = true
            }

            var conversation: Conversation? = null
            try {
                val conversationConfig = ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = TOP_K,
                        topP = TOP_P,
                        temperature = TEMPERATURE
                    )
                )
                conversation = loadedEngine.createConversation(conversationConfig)
                val contents = buildList {
                    if (image != null) add(Content.ImageBytes(image.toPngBytes()))
                    add(Content.Text(prompt))
                }
                conversation.sendMessage(Contents.of(contents))
                    .contents
                    .contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            } finally {
                try {
                    conversation?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing conversation", e)
                }
                synchronized(lock) {
                    generating = false
                    if (closeRequested) {
                        closeRequested = false
                        releaseEngine()
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
                releaseEngine()
            }
        }
    }

    // Returns the cached engine, reloading it if the model path changed.
    // Caller must hold [lock]; throws if the native model cannot be loaded.
    private fun initialize(context: Context, modelPath: String): Engine {
        val existing = engine
        if (existing != null && loadedModelPath == modelPath) return existing
        releaseEngine()
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            visionBackend = Backend.GPU(),
            maxNumTokens = MAX_TOKENS,
            maxNumImages = 1,
            cacheDir = context.cacheDir.path
        )
        val newEngine = Engine(config)
        try {
            newEngine.initialize()
        } catch (e: Exception) {
            newEngine.close()
            throw e
        }
        engine = newEngine
        loadedModelPath = modelPath
        return newEngine
    }

    /** Frees the native model. Caller must hold [lock]. */
    private fun releaseEngine() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM", e)
        }
        engine = null
        loadedModelPath = null
    }

    private fun Bitmap.toPngBytes(): ByteArray =
        ByteArrayOutputStream().use { stream ->
            compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
}
