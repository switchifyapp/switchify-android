package com.enaboapps.switchify.service.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

object LlmManager {
    private const val TAG = "LlmManager"
    private const val TOP_K = 40
    private const val TOP_P = 0.95
    private const val TEMPERATURE = 0.7
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
    private var engine: Engine? = null
    private var loadedModelPath: String? = null
    private var generating = false
    private var closeRequested = false

    // Caller must hold [lock].
    private fun initialize(modelPath: String): Boolean {
        if (engine != null && loadedModelPath == modelPath) return true
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model file not found at $modelPath")
            return false
        }
        releaseEngine()
        return try {
            engine = createEngine(modelPath)
            loadedModelPath = modelPath
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            releaseEngine()
            false
        }
    }

    private fun createEngine(modelPath: String): Engine =
        try {
            buildEngine(modelPath, Backend.GPU())
        } catch (e: Exception) {
            Log.w(TAG, "GPU backend unavailable, falling back to CPU", e)
            buildEngine(modelPath, Backend.CPU())
        }

    private fun buildEngine(modelPath: String, backend: Backend): Engine {
        val created = Engine(EngineConfig(modelPath = modelPath, backend = backend))
        created.initialize()
        return created
    }

    fun generateReplySuggestions(
        context: Context,
        bitmap: Bitmap,
        modelPath: String,
        onResult: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val activeEngine: Engine
        synchronized(lock) {
            if (!initialize(modelPath)) {
                onError("Could not load the language model")
                return
            }
            activeEngine = engine ?: run {
                onError("Could not load the language model")
                return
            }
            generating = true
        }

        var screenshot: File? = null
        try {
            val file = writeScreenshot(context, downscale(bitmap))
            screenshot = file
            val response = activeEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = TOP_K,
                        topP = TOP_P,
                        temperature = TEMPERATURE
                    )
                )
            ).use { conversation ->
                conversation.sendMessage(
                    Contents.of(
                        Content.ImageFile(file.absolutePath),
                        Content.Text(PROMPT)
                    )
                )
            }
            onResult(parseSuggestions(response.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error generating reply suggestions", e)
            onError("Could not generate replies")
        } finally {
            screenshot?.delete()
            synchronized(lock) {
                generating = false
                if (closeRequested) {
                    closeRequested = false
                    releaseEngine()
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

    private fun writeScreenshot(context: Context, bitmap: Bitmap): File {
        val file = File.createTempFile("reply_drafter", ".png", context.cacheDir)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
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
                releaseEngine()
            }
        }
    }

    /** Frees the native engine. The caller must hold [lock]. */
    private fun releaseEngine() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM", e)
        }
        engine = null
        loadedModelPath = null
    }
}
