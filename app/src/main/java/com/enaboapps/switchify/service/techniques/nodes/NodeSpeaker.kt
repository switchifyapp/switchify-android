package com.enaboapps.switchify.service.techniques.nodes

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import java.util.Locale
import java.util.UUID

/**
 * This object is responsible for speaking the node information.
 * It uses the Android TTS (Text-to-Speech) API to speak the node information.
 * Provides configuration options and callbacks for TTS events.
 */
object NodeSpeaker {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var defaultLocale = Locale.US
    private var defaultPitch = 1.0f
    private var defaultSpeechRate = 1.0f
    private var onSpeakCompleteListener: ((String) -> Unit)? = null
    private var onErrorListener: ((String, Int) -> Unit)? = null

    /**
     * Initializes the TTS object with default configuration.
     *
     * @param context The context to use for initializing the TTS object.
     * @param onInitialized Callback triggered when TTS is successfully initialized.
     * @param onError Callback triggered when TTS initialization fails.
     */
    fun init(
        context: Context,
        onInitialized: (() -> Unit)? = null,
        onError: ((Int) -> Unit)? = null
    ) {
        tts = TextToSpeech(context.applicationContext) { status ->
            isInitialized = status == TextToSpeech.SUCCESS

            if (isInitialized) {
                configureTTS()
                onInitialized?.invoke()
            } else {
                onError?.invoke(status)
            }
        }
    }

    /**
     * Configures the TTS engine with default settings and utterance progress listener.
     */
    private fun configureTTS() {
        tts?.apply {
            language = defaultLocale
            setPitch(defaultPitch)
            setSpeechRate(defaultSpeechRate)

            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    // Called when TTS starts speaking
                }

                override fun onDone(utteranceId: String) {
                    onSpeakCompleteListener?.invoke(utteranceId)
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    onErrorListener?.invoke(utteranceId, errorCode)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    onErrorListener?.invoke(utteranceId, -1)
                }
            })
        }
    }

    /**
     * Speaks the node information with specified parameters.
     *
     * @param node The node to speak.
     * @param queueMode The queue mode to use (QUEUE_FLUSH or QUEUE_ADD).
     * @param onComplete Callback triggered when speaking is complete.
     * @return The utterance ID if speaking was initiated, null otherwise.
     */
    fun speakNode(
        node: ScanNodeInterface,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onComplete: ((String) -> Unit)? = null
    ): String? {
        if (!isInitialized) {
            println("TTS not initialized")
            return null
        }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        onComplete?.let { callback ->
            onSpeakCompleteListener = callback
        }

        val content = node.getContentDescription()
        println("Speaking: $content")

        tts?.speak(content, queueMode, params, utteranceId)

        return utteranceId
    }

    /**
     * Speaks a row or group of nodes with specified parameters.
     * Announces the first node and number of nodes in the group.
     *
     * @param nodes The list of nodes to speak.
     * @param isGroup Whether the nodes are part of a group.
     * @param queueMode The queue mode to use (QUEUE_FLUSH or QUEUE_ADD).
     * @param onComplete Callback triggered when speaking is complete.
     * @return The utterance ID if speaking was initiated, null otherwise.
     */
    fun speakNodes(
        nodes: List<ScanNodeInterface>,
        isGroup: Boolean,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onComplete: ((String) -> Unit)? = null
    ) {
        if (!isInitialized || nodes.isEmpty()) {
            println("TTS not initialized or nodes are empty")
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        onComplete?.let { callback ->
            onSpeakCompleteListener = callback
        }

        var spokeText =
            if (isGroup) "Group of ${nodes.size} items" else "Row of ${nodes.size} items"
        val firstNode = nodes.first()
        spokeText += " starting at ${firstNode.getContentDescription()}"
        println("Speaking: $spokeText")

        tts?.speak(spokeText, queueMode, params, utteranceId)
    }

    /**
     * Stops speaking immediately.
     */
    fun stopSpeaking() {
        if (isInitialized) {
            tts?.stop()
        } else {
            println("TTS not initialized")
        }
    }

    /**
     * Updates the TTS configuration settings.
     *
     * @param locale The locale to use for TTS.
     * @param pitch The pitch to use for speaking (0.5 to 2.0).
     * @param speechRate The speech rate to use (0.5 to 2.0).
     */
    fun updateConfiguration(
        locale: Locale = defaultLocale,
        pitch: Float = defaultPitch,
        speechRate: Float = defaultSpeechRate
    ) {
        defaultLocale = locale
        defaultPitch = pitch.coerceIn(0.5f, 2.0f)
        defaultSpeechRate = speechRate.coerceIn(0.5f, 2.0f)

        if (isInitialized) {
            configureTTS()
        }
    }

    /**
     * Sets the error listener for TTS operations.
     *
     * @param listener The listener to call when an error occurs.
     */
    fun setOnErrorListener(listener: (utteranceId: String, errorCode: Int) -> Unit) {
        onErrorListener = listener
    }

    /**
     * Releases TTS resources. Should be called when TTS is no longer needed.
     */
    fun shutdown() {
        tts?.apply {
            stop()
            shutdown()
        }
        tts = null
        isInitialized = false
        onSpeakCompleteListener = null
        onErrorListener = null
    }
}