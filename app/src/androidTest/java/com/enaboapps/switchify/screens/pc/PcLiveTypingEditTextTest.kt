package com.enaboapps.switchify.screens.pc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.pc.PcKeyboardKey
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PcLiveTypingEditTextTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun composingTextStaysLocalUntilCommitted() {
        val committedText = mutableListOf<String>()
        setLiveInput(onTextCommitted = committedText::add)

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.setComposingText("hel", 1)
            assertEquals(emptyList<String>(), committedText)
            input?.commitText("hello", 1)
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("hello"), committedText)
        }
    }

    @Test
    fun deletionConsumesLocalCompositionBeforeSendingBackspace() {
        val keys = mutableListOf<PcKeyboardKey>()
        setLiveInput(onKeyCommitted = keys::add)

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.setComposingText("ab", 1)
            input?.deleteSurroundingText(3, 0)
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf(PcKeyboardKey.Backspace), keys)
        }
    }

    @Test
    fun editorActionCommitsPendingCompositionBeforeEnter() {
        val events = mutableListOf<Any>()
        setLiveInput(
            onTextCommitted = events::add,
            onKeyCommitted = events::add
        )

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.setComposingText("Hi", 1)
            input?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("Hi", PcKeyboardKey.Enter), events)
        }
    }

    @Test
    fun commitTextPreservesUnicodeAndVoiceSizedChunks() {
        val committedText = mutableListOf<String>()
        setLiveInput(onTextCommitted = committedText::add)

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.commitText("Hello from voice 👋", 1)
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("Hello from voice 👋"), committedText)
        }
    }

    @Test
    fun pasteSendsClipboardTextImmediately() {
        val committedText = mutableListOf<String>()
        setLiveInput(onTextCommitted = committedText::add)

        onLiveTypingView { editText ->
            val clipboard = editText.context.getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("PC typing", "Pasted text"))
            editText.onTextContextMenuItem(android.R.id.paste)
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("Pasted text"), committedText)
        }
    }

    @Test
    fun hardwareDeleteAndTabMapToRemoteKeys() {
        val keys = mutableListOf<PcKeyboardKey>()
        setLiveInput(onKeyCommitted = keys::add)

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            input?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL))
            input?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
        }

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(PcKeyboardKey.Backspace, PcKeyboardKey.Delete, PcKeyboardKey.Tab),
                keys
            )
        }
    }

    @Test
    fun rejectedCommitRemainsVisibleUntilRetryIsAccepted() {
        val attempts = mutableListOf<String>()
        var accepting = false
        setLiveInput(
            onTextCommitted = {
                attempts += it
                accepting
            }
        )

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.commitText("Keep me", 1)
            assertEquals("Keep me", editText.text.toString())

            accepting = true
            editText.retryPendingText()
            assertEquals("", editText.text.toString())
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("Keep me", "Keep me"), attempts)
        }
    }

    private fun setLiveInput(
        onTextCommitted: (String) -> Boolean = { false },
        onKeyCommitted: (PcKeyboardKey) -> Unit = {}
    ) {
        composeTestRule.setContent {
            SwitchifyTheme {
                PcTypingInput(
                    text = "",
                    message = null,
                    typingMode = PcTypingMode.Live,
                    liveTypingAvailable = true,
                    liveTypingPaused = false,
                    liveTypingMessage = null,
                    onTypingModeSelected = {},
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = {},
                    onLiveTypingStarted = {},
                    onLiveTypingStopped = {},
                    onLiveTextCommitted = onTextCommitted,
                    onLiveKeyCommitted = onKeyCommitted,
                    onLiveTypingRetry = {},
                    enabled = true
                )
            }
        }
    }

    private fun onLiveTypingView(action: (PcLiveTypingEditText) -> Unit) {
        onView(isAssignableFrom(PcLiveTypingEditText::class.java))
            .perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> =
                    isAssignableFrom(PcLiveTypingEditText::class.java)

                override fun getDescription(): String = "interact with live typing input"

                override fun perform(uiController: UiController, view: View) {
                    action(view as PcLiveTypingEditText)
                    uiController.loopMainThreadUntilIdle()
                }
            })
    }
}
