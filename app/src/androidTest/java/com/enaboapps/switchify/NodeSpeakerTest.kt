package com.enaboapps.switchify

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.NodeSpeaker
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NodeSpeakerTest {
    private lateinit var context: Context
    private val testTimeout = 5L // 5 seconds timeout
    private var lastError: Pair<String, Int>? = null
    private var speakingCompleted = false

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        lastError = null
        speakingCompleted = false
    }

    @After
    fun tearDown() {
        NodeSpeaker.shutdown()
    }

    @Test
    fun testBasicSpeech() {
        val initLatch = CountDownLatch(1)
        var initializationSuccess = false

        NodeSpeaker.init(
            context = context,
            onInitialized = {
                initializationSuccess = true
                initLatch.countDown()
            },
            onError = { status ->
                initializationSuccess = false
                initLatch.countDown()
            }
        )

        assertTrue("TTS initialization timed out", initLatch.await(testTimeout, TimeUnit.SECONDS))
        assertTrue("TTS failed to initialize", initializationSuccess)

        val speakLatch = CountDownLatch(1)
        val testText = "Hello, world!"
        val node = Node().apply {
            contentDescription = testText
        }

        NodeSpeaker.setOnErrorListener { id, errorCode ->
            lastError = id to errorCode
            speakLatch.countDown()
        }

        val utteranceId = NodeSpeaker.speakNode(
            node = node,
            onComplete = { id ->
                speakingCompleted = true
                speakLatch.countDown()
            }
        )

        assertNotNull("Failed to start speaking", utteranceId)
        assertTrue("Speaking timed out", speakLatch.await(testTimeout, TimeUnit.SECONDS))
        assertNull("Speaking encountered an error: $lastError", lastError)
        assertTrue("Speaking did not complete successfully", speakingCompleted)
    }

    @Test
    fun testRapidChanges() {
        // Initialize TTS first
        val initLatch = CountDownLatch(1)
        NodeSpeaker.init(
            context = context,
            onInitialized = { initLatch.countDown() },
            onError = { initLatch.countDown() }
        )
        assertTrue("TTS initialization timed out", initLatch.await(testTimeout, TimeUnit.SECONDS))

        // Test data
        data class SpeechAttempt(
            val text: String,
            var completed: Boolean = false,
            var error: Pair<String, Int>? = null
        )

        val attempts = listOf(
            SpeechAttempt("First message"),
            SpeechAttempt("Second message"),
            SpeechAttempt("Third message"),
            SpeechAttempt("Fourth message")
        )

        attempts.forEachIndexed { index, attempt ->
            val speechLatch = CountDownLatch(1)

            NodeSpeaker.setOnErrorListener { id, errorCode ->
                attempt.error = id to errorCode
                speechLatch.countDown()
            }

            val node = Node().apply {
                contentDescription = attempt.text
            }

            val utteranceId = NodeSpeaker.speakNode(
                node = node,
                onComplete = { id ->
                    attempt.completed = true
                    speechLatch.countDown()
                }
            )

            assertNotNull("Failed to start speaking attempt ${index + 1}", utteranceId)

            // Wait a short time to simulate rapid changes
            Thread.sleep(400)

            // Stop current speech before starting next one
            NodeSpeaker.stopSpeaking()
        }
    }

}