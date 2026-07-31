package com.enaboapps.switchify.screens.pc

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.pc.PcKeyboardKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PcQuickInputSheetUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sheetShowsSharedDraftAndFocusesEditor() {
        composeTestRule.setContent {
            SwitchifyTheme {
                PcQuickInputContent(
                    typingText = "Shared draft",
                    typingMessage = null,
                    connected = true,
                    enabled = true,
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = {},
                    onKeySelected = {},
                    onClose = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Shared draft").assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun closeDismissesWithoutClearingDraft() {
        var dismissCount = 0
        var clearCount = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.setContent {
            SwitchifyTheme {
                PcQuickInputContent(
                    typingText = "Keep me",
                    typingMessage = null,
                    connected = true,
                    enabled = true,
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = { clearCount++ },
                    onKeySelected = {},
                    onClose = { dismissCount++ }
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.pc_control_quick_input_close))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, dismissCount)
            assertEquals(0, clearCount)
        }
    }

    @Test
    fun invalidDraftShowsErrorAndDisablesSend() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val errorMessage = "Unsupported text"

        composeTestRule.setContent {
            SwitchifyTheme {
                PcQuickInputContent(
                    typingText = "Invalid\u001Btext",
                    typingMessage = errorMessage,
                    connected = true,
                    enabled = true,
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = {},
                    onKeySelected = {},
                    onClose = {}
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_send))
            .assertIsNotEnabled()
    }

    @Test
    fun navigationTilesSendOneMatchingKeyPerTap() {
        val selectedKeys = mutableListOf<PcKeyboardKey>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedKeys =
            (pcEditingKeySpecs() + pcSpacingKeySpecs() + pcCursorKeySpecs()).map { it.key }

        composeTestRule.setContent {
            SwitchifyTheme {
                PcQuickInputContent(
                    typingText = "",
                    typingMessage = null,
                    connected = true,
                    enabled = true,
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = {},
                    onKeySelected = selectedKeys::add,
                    onClose = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_pc_keys))
            .performClick()

        (pcEditingKeySpecs() + pcSpacingKeySpecs() + pcCursorKeySpecs()).forEach { spec ->
            composeTestRule.onNodeWithText(context.getString(spec.labelResId)).performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(expectedKeys, selectedKeys)
        }
    }

    @Test
    fun busyTransitionDoesNotReopenHiddenKeyboard() {
        var enabled by mutableStateOf(true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.setContent {
            SwitchifyTheme {
                PcQuickInputContent(
                    typingText = "",
                    typingMessage = null,
                    connected = true,
                    enabled = enabled,
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = {},
                    onKeySelected = {},
                    onClose = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                context.getString(R.string.pc_control_quick_input_hide_keyboard)
            )
            .performClick()
        composeTestRule.runOnIdle { enabled = false }
        composeTestRule.runOnIdle { enabled = true }

        composeTestRule.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun liveModeShowsCompactStatusLine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.setContent {
            SwitchifyTheme {
                PcQuickInputContent(
                    typingText = "Preserved draft",
                    typingMessage = null,
                    typingMode = PcTypingMode.Live,
                    liveTypingAvailable = true,
                    connected = true,
                    enabled = true,
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = {},
                    onKeySelected = {},
                    onClose = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_live_status_line))
            .assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("Preserved draft").fetchSemanticsNodes().size
        )
    }

    @Test
    fun contextualActionsSwitchBetweenTypeAndKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.setContent {
            SwitchifyTheme {
                PcQuickInputContent(
                    typingText = "",
                    typingMessage = null,
                    connected = true,
                    enabled = true,
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = {},
                    onKeySelected = {},
                    onClose = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_control_quick_type))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_pc_keys))
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_back_to_typing))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_control_quick_input_open_full))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_back_to_typing))
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_type_on_pc))
            .assertIsDisplayed()
    }

    @Test
    fun keysPageKeepsLiveSessionActive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var startedCount = 0
        var stoppedCount = 0
        var clearCount = 0
        var liveText by mutableStateOf("Clear me")
        val selectedKeys = mutableListOf<PcKeyboardKey>()

        composeTestRule.setContent {
            SwitchifyTheme {
                PcQuickInputContent(
                    typingText = "",
                    typingMessage = null,
                    typingMode = PcTypingMode.Live,
                    liveTypingAvailable = true,
                    liveTypingText = liveText,
                    connected = true,
                    enabled = true,
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = {},
                    onLiveTypingStarted = { startedCount += 1 },
                    onLiveTypingStopped = { stoppedCount += 1 },
                    onLiveSessionTextChanged = { liveText = it },
                    onClearLiveField = {
                        liveText = ""
                        clearCount += 1
                    },
                    onKeySelected = selectedKeys::add,
                    onClose = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_pc_keys))
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_key_enter))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, startedCount)
            assertEquals(0, stoppedCount)
            assertEquals(1, clearCount)
            assertEquals("", liveText)
            assertEquals(listOf(PcKeyboardKey.Enter), selectedKeys)
        }
    }

    @Test
    fun unavailableLiveModeFallsBackToDraftWithExplanation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.setContent {
            SwitchifyTheme {
                PcQuickInputContent(
                    typingText = "Preserved draft",
                    typingMessage = null,
                    typingMode = PcTypingMode.Live,
                    liveTypingAvailable = false,
                    connected = true,
                    enabled = true,
                    onTextChanged = {},
                    onSend = {},
                    onSendAndEnter = {},
                    onClear = {},
                    onKeySelected = {},
                    onClose = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_live_unavailable))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Preserved draft").assertIsDisplayed()
    }
}
