package com.enaboapps.switchify.screens.pc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enaboapps.switchify.R
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
        val events = mutableListOf<String>()
        setLiveInput(
            onTextCommitted = {
                events += "text:$it"
                true
            },
            onEnterCommitted = {
                events += "enter"
                true
            }
        )

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.setComposingText("Hi", 1)
            input?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("text:Hi", "enter"), events)
        }
    }

    @Test
    fun newlineOnlyImeCommitUsesEnterWithoutAppendingText() {
        val committedText = mutableListOf<String>()
        var enterCount = 0
        setLiveInput(
            onTextCommitted = committedText::add,
            onEnterCommitted = {
                enterCount += 1
                true
            }
        )

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.commitText("Hi", 1)
            committedText.clear()
            input?.commitText("\n", 1)

            assertEquals("Hi", editText.text.toString())
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("Hi"), committedText)
            assertEquals(1, enterCount)
        }
    }

    @Test
    fun hardwareAndNumpadEnterUseTheEnterCallback() {
        var enterCount = 0
        setLiveInput(
            onTextCommitted = { true },
            onEnterCommitted = {
                enterCount += 1
                true
            }
        )

        onLiveTypingView { editText ->
            editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_NUMPAD_ENTER))
            editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_NUMPAD_ENTER))
        }

        composeTestRule.runOnIdle {
            assertEquals(2, enterCount)
        }
    }

    @Test
    fun acceptedEnterClearsTheVisibleField() {
        var liveText by mutableStateOf("Ready")
        composeTestRule.setContent {
            SwitchifyTheme {
                PcTypingEditor(
                    headingResId = R.string.pc_typing_type_on_pc,
                    typingText = "",
                    typingMessage = null,
                    typingDraftReviewWarning = false,
                    typingMode = PcTypingMode.Live,
                    draftPressEnterAfterSending = false,
                    liveTypingAvailable = true,
                    liveTypingPaused = false,
                    liveTypingMessage = null,
                    liveTypingAnnouncement = null,
                    liveTypingNotice = null,
                    liveTypingText = liveText,
                    enabled = true,
                    onTypingModeSelected = {},
                    onDraftEnterChanged = {},
                    onTextChanged = {},
                    onSendDraft = {},
                    onClear = {},
                    onClearLiveField = {},
                    onLiveSessionTextChanged = { liveText = it },
                    onLiveTextCommitted = { true },
                    onLiveEnterCommitted = {
                        liveText = ""
                        true
                    },
                    onLiveKeyCommitted = {},
                    onLiveTypingRetry = {},
                    onMoveLiveToDraft = {},
                    onLiveTypingAnnouncementShown = {},
                    onLiveTypingNoticeShown = {},
                    onOpenKeys = {},
                    fieldFocusRequester = remember { FocusRequester() }
                )
            }
        }

        onLiveTypingView { editText ->
            editText.onCreateInputConnection(EditorInfo())
                ?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }

        onView(isAssignableFrom(PcLiveTypingEditText::class.java))
            .check(matches(withText("")))
    }

    @Test
    fun rejectedEnterRetainsTheVisibleField() {
        setLiveInput(
            onTextCommitted = { true },
            onEnterCommitted = { false }
        )

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.commitText("Keep me", 1)
            input?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }

        onView(isAssignableFrom(PcLiveTypingEditText::class.java))
            .check(matches(withText("Keep me")))
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
    fun hardwareDeleteOutsideTheSessionUsesRemoteKeysAndTabStaysVisible() {
        val keys = mutableListOf<PcKeyboardKey>()
        val committedText = mutableListOf<String>()
        setLiveInput(
            onTextCommitted = committedText::add,
            onKeyCommitted = keys::add
        )

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            input?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL))
            input?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
        }

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(PcKeyboardKey.Backspace, PcKeyboardKey.Delete),
                keys
            )
            assertEquals(listOf("\t"), committedText)
        }
    }

    @Test
    fun dispatchedHardwareCharactersAreSentImmediately() {
        val committedText = mutableListOf<String>()
        setLiveInput(onTextCommitted = committedText::add)

        onLiveTypingView { editText ->
            editText.dispatchKeyEvent(
                KeyEvent(
                    0L,
                    0L,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_A,
                    0
                )
            )
            editText.dispatchKeyEvent(
                KeyEvent(
                    0L,
                    0L,
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_A,
                    0
                )
            )
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("a"), committedText)
        }
    }

    @Test
    fun disposalFlushRetainsRejectedComposition() {
        setLiveInput(onTextCommitted = { false })

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.setComposingText("Keep me", 1)

            assertEquals("Keep me", editText.finishCompositionAndTakeSessionText())
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
            editText.retryCommittedText()
            assertEquals("Keep me", editText.text.toString())
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("Keep me", "Keep me"), attempts)
        }
    }

    @Test
    fun committedTextStaysVisibleAndPredictionReplacementSendsANewSnapshot() {
        val committedText = mutableListOf<String>()
        setLiveInput(onTextCommitted = committedText::add)

        onLiveTypingView { editText ->
            val input = editText.onCreateInputConnection(EditorInfo())
            input?.commitText("teh ", 1)
            input?.setSelection(0, 3)
            input?.commitText("the", 1)

            assertEquals("the ", editText.text.toString())
        }

        composeTestRule.runOnIdle {
            assertEquals(listOf("teh ", "the "), committedText)
        }
    }

    @Test
    fun returningFromPcKeysClearsLiveTextAndRestoresEditorFocus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var clearCount = 0
        var liveText by mutableStateOf("Clear me")
        composeTestRule.setContent {
            SwitchifyTheme {
                PcTypingControlScreen(
                    typingText = "",
                    typingMessage = null,
                    typingDraftReviewWarning = false,
                    typingMode = PcTypingMode.Live,
                    draftPressEnterAfterSending = false,
                    liveTypingAvailable = true,
                    liveTypingPaused = false,
                    liveTypingMessage = null,
                    liveTypingAnnouncement = null,
                    liveTypingNotice = null,
                    liveTypingText = liveText,
                    enabled = true,
                    onTypingModeSelected = {},
                    onDraftEnterChanged = {},
                    onTextChanged = {},
                    onSendDraft = {},
                    onClear = {},
                    onClearLiveField = {
                        liveText = ""
                        clearCount += 1
                    },
                    onLiveTypingStarted = {},
                    onLiveTypingStopped = {},
                    onLiveSessionTextChanged = { liveText = it },
                    onLiveTextCommitted = { true },
                    onLiveEnterCommitted = { true },
                    onLiveKeyCommitted = {},
                    onLiveTypingRetry = {},
                    onMoveLiveToDraft = {},
                    onLiveTypingAnnouncementShown = {},
                    onLiveTypingNoticeShown = {},
                    onKeySelected = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_pc_keys))
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, clearCount)
            assertEquals("", liveText)
        }
        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_back_to_typing))
            .performClick()

        onView(isAssignableFrom(PcLiveTypingEditText::class.java))
            .check(matches(hasFocus()))
            .check(matches(withText("")))
    }

    private fun setLiveInput(
        onTextCommitted: (String) -> Boolean = { false },
        onSessionTextChanged: (String) -> Unit = {},
        onEnterCommitted: () -> Boolean = { false },
        onKeyCommitted: (PcKeyboardKey) -> Unit = {}
    ) {
        composeTestRule.setContent {
            SwitchifyTheme {
                PcTypingEditor(
                    headingResId = com.enaboapps.switchify.R.string.pc_typing_type_on_pc,
                    typingText = "",
                    typingMessage = null,
                    typingDraftReviewWarning = false,
                    typingMode = PcTypingMode.Live,
                    draftPressEnterAfterSending = false,
                    liveTypingAvailable = true,
                    liveTypingPaused = false,
                    liveTypingMessage = null,
                    liveTypingAnnouncement = null,
                    liveTypingNotice = null,
                    liveTypingText = "",
                    enabled = true,
                    onTypingModeSelected = {},
                    onDraftEnterChanged = {},
                    onTextChanged = {},
                    onSendDraft = {},
                    onClear = {},
                    onClearLiveField = {},
                    onLiveSessionTextChanged = onSessionTextChanged,
                    onLiveTextCommitted = onTextCommitted,
                    onLiveEnterCommitted = onEnterCommitted,
                    onLiveKeyCommitted = onKeyCommitted,
                    onLiveTypingRetry = {},
                    onMoveLiveToDraft = {},
                    onLiveTypingAnnouncementShown = {},
                    onLiveTypingNoticeShown = {},
                    onOpenKeys = {},
                    fieldFocusRequester = remember { FocusRequester() }
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
