package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class SettingsStudySortComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun updatesStatusAndSavesSelectedSortMode() {
        val savedMode = AtomicReference<String>()
        composeRule.setContent {
            SettingsNewCardSortPanel(
                model = SettingsNewCardSortPanelModel(
                    title = SettingsTextCopy.newCardSortTitle(),
                    body = SettingsTextCopy.newCardSortBody(),
                    initialMode = RecordsBase.NEW_CARD_SORT_FREQUENCY,
                    options = listOf(
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                            RecordsBase.NEW_CARD_SORT_FREQUENCY,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY)
                        ),
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK)
                        )
                    ),
                    saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
                    onSave = SettingsNewCardSortSaver { savedMode.set(it) }
                )
            )
        }

        composeRule
            .onNodeWithText(SettingsTextCopy.newCardSortStatusText(RecordsBase.NEW_CARD_SORT_FREQUENCY))
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("new-card-sort-option-${RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK}")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText(SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
            .assertExists()
        composeRule
            .onNodeWithText(SettingsTextCopy.newCardSortStatusText(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
            .assertExists()
        composeRule.onNodeWithText("Save new card sort").performScrollTo().assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, savedMode.get())
        }
    }

    @Test
    fun rendersAllSortOptionsWithLiteralSelectorsAndSavesSelectedMode() {
        val savedMode = AtomicReference<String>()

        composeRule.setContent {
            SettingsNewCardSortPanel(
                model = SettingsNewCardSortPanelModel(
                    title = SettingsTextCopy.newCardSortTitle(),
                    body = SettingsTextCopy.newCardSortBody(),
                    initialMode = RecordsBase.NEW_CARD_SORT_FREQUENCY,
                    options = listOf(
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                            RecordsBase.NEW_CARD_SORT_FREQUENCY,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                        ),
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY),
                            RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY),
                        ),
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                        ),
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS),
                            RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS),
                        ),
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY),
                            RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY),
                        ),
                    ),
                    saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
                    onSave = SettingsNewCardSortSaver { savedMode.set(it) },
                )
            )
        }

        composeRule.onNodeWithText("Frequency").assertIsEnabled().performScrollTo().performClick()
        composeRule.onNodeWithText("Anki difficulty").assertIsEnabled().performScrollTo().performClick()
        composeRule.onNodeWithText("Retrievability risk").assertIsEnabled().performScrollTo().performClick()
        composeRule.onNodeWithText("Kani weakness").assertIsEnabled().performScrollTo().performClick()
        composeRule.onNodeWithText("Balanced priority").assertIsEnabled().performScrollTo().performClick()
        composeRule.onNodeWithText("Save new card sort").assertIsEnabled().performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY, savedMode.get())
        }
    }

    @Test
    fun updatesPreviewRowsForSelectedSortMode() {
        composeRule.setContent {
            SettingsNewCardSortPanel(
                model = SettingsNewCardSortPanelModel(
                    title = SettingsTextCopy.newCardSortTitle(),
                    body = SettingsTextCopy.newCardSortBody(),
                    initialMode = RecordsBase.NEW_CARD_SORT_FREQUENCY,
                    options = listOf(
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                            RecordsBase.NEW_CARD_SORT_FREQUENCY,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                        ),
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                        ),
                    ),
                    saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
                    previewRowsByMode = mapOf(
                        RecordsBase.NEW_CARD_SORT_FREQUENCY to listOf(
                            SettingsNewCardSortPreviewRowModel("日", "sun", "#1 frequency"),
                        ),
                        RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK to listOf(
                            SettingsNewCardSortPreviewRowModel("難", "difficult", "Risk 82%"),
                        ),
                    ),
                    onSave = SettingsNewCardSortSaver {},
                )
            )
        }

        composeRule.onNodeWithText("Next up preview").assertExists()
        composeRule.onNodeWithText("日").assertExists()
        composeRule.onNodeWithText("sun").assertExists()
        composeRule.onNodeWithText("#1 frequency").assertExists()
        composeRule.onAllNodesWithText("難").assertCountEquals(0)

        composeRule
            .onNodeWithTag("new-card-sort-option-${RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK}")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("難").assertExists()
        composeRule.onNodeWithText("difficult").assertExists()
        composeRule.onNodeWithText("Risk 82%").assertExists()
        composeRule.onAllNodesWithText("日").assertCountEquals(0)
    }

    @Test
    fun updatesPreviewWarningForSelectedSortMode() {
        val warning = SettingsTextCopy.newCardSortConfusablePreviewWarning(listOf("人/入"))
        composeRule.setContent {
            SettingsNewCardSortPanel(
                model = SettingsNewCardSortPanelModel(
                    title = SettingsTextCopy.newCardSortTitle(),
                    body = SettingsTextCopy.newCardSortBody(),
                    initialMode = RecordsBase.NEW_CARD_SORT_FREQUENCY,
                    options = listOf(
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                            RecordsBase.NEW_CARD_SORT_FREQUENCY,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                        ),
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                        ),
                    ),
                    saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
                    previewRowsByMode = mapOf(
                        RecordsBase.NEW_CARD_SORT_FREQUENCY to listOf(
                            SettingsNewCardSortPreviewRowModel("人", "person", "#1 frequency"),
                            SettingsNewCardSortPreviewRowModel("入", "enter", "#2 frequency"),
                        ),
                        RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK to listOf(
                            SettingsNewCardSortPreviewRowModel("難", "difficult", "Risk 82%"),
                        ),
                    ),
                    previewWarningsByMode = mapOf(RecordsBase.NEW_CARD_SORT_FREQUENCY to warning),
                    onSave = SettingsNewCardSortSaver {},
                )
            )
        }

        composeRule.onNodeWithText(warning).assertExists()

        composeRule
            .onNodeWithTag("new-card-sort-option-${RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK}")
            .performScrollTo()
            .performClick()

        composeRule.onAllNodesWithText(warning).assertCountEquals(0)
    }
}
