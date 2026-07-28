package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsNewCardSortComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedSortOptionIsHighlightedAndSelectionMovesOnClick() {
        composeRule.setContent {
            SettingsNewCardSortPanel(
                SettingsNewCardSortPanelModel(
                    title = "New card sort",
                    body = "Choose the order used for new cards.",
                    initialMode = "balanced",
                    options = listOf(
                        SettingsNewCardSortOptionModel(
                            label = "Frequency",
                            mode = "frequency",
                            description = "Jiten frequency first.",
                        ),
                        SettingsNewCardSortOptionModel(
                            label = "Balanced mix",
                            mode = "balanced",
                            description = "Balances misses, risk, and frequency.",
                        ),
                    ),
                    saveLabel = "Save sort",
                    onSave = SettingsNewCardSortSaver {},
                ),
            )
        }

        val frequency = composeRule.onNodeWithTag("new-card-sort-option-frequency")
        val balanced = composeRule.onNodeWithTag("new-card-sort-option-balanced")

        balanced.assertIsSelected()
        frequency.assertIsNotSelected()

        frequency.performClick()

        frequency.assertIsSelected()
        balanced.assertIsNotSelected()
    }
}
