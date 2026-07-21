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
        composeTestRule
            .onNodeWithText(context.getString(R.string.pc_typing_send_enter))
            .assertIsNotEnabled()
    }

    @Test
    fun navigationTilesSendOneMatchingKeyPerTap() {
        val selectedKeys = mutableListOf<PcKeyboardKey>()
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
                    onKeySelected = selectedKeys::add,
                    onClose = {}
                )
            }
        }

        pcKeyboardNavigationKeys().forEach { key ->
            composeTestRule.onNodeWithText(context.getString(key.labelResId)).performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(pcKeyboardNavigationKeys(), selectedKeys)
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
}
