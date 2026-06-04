package dev.bee.kanjianki

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Rule
import org.junit.Test

class MainActivitySettingsCategoryComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersAndTogglesCategoryHeaderDescription() {
        composeRule.setContent {
            MaterialTheme {
                val expandedState = remember { mutableStateOf(false) }
                var expanded by expandedState
                SettingsCategoryHeader(
                    title = "Deck options",
                    summary = "How much appears today, how quickly repeats return, and when cards move rungs.",
                    iconRes = R.drawable.ic_study_24,
                    iconTint = ComposeColor(0xFFDA3A7A),
                    borderColor = ComposeColor(0xFFFFC7DE),
                    expanded = expanded,
                    countText = "5 cards",
                    titleColor = ComposeColor(0xFF4B2552),
                    summaryColor = ComposeColor(0xFF826084),
                    countColor = ComposeColor(0xFFDA3A7A),
                    contentDescription = SettingsTextCopy.categoryToggleDescription(expanded, "Deck options"),
                    testTagKey = "settings-study-behavior",
                    onToggle = { expanded = !expanded }
                )
            }
        }

        composeRule.onNodeWithText("Deck options").assertIsDisplayed()
        composeRule.onNodeWithText("How much appears today, how quickly repeats return, and when cards move rungs.").assertIsDisplayed()
        composeRule.onNodeWithText("5 cards").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Expand Deck options").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Expand Deck options").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Collapse Deck options").assertIsDisplayed()
    }
}
