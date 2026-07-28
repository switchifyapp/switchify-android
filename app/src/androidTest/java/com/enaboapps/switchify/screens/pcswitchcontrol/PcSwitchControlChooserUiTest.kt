package com.enaboapps.switchify.screens.pcswitchcontrol

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PcSwitchControlChooserUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun profileRowsExposeRadioGroupSelectionSemantics() {
        val selected = profile("builtin.keyboard", "Generic keyboard")
        val catalog = PcSwitchProfileCatalog(
            catalogRevision = 1,
            profiles = listOf(selected, profile("custom", "Custom"))
        )

        composeTestRule.setContent {
            SwitchifyTheme {
                PcSwitchProfileList(
                    catalog = catalog,
                    selected = selected,
                    enabled = true,
                    onSelected = {}
                )
            }
        }

        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assertExists()
        composeTestRule.onNodeWithText("Generic keyboard").assertIsSelected()
    }

    private fun profile(id: String, name: String) = PcSwitchProfileSummary(
        id = id,
        version = 1,
        name = name,
        kind = "mapped",
        bindings = emptyList()
    )
}
