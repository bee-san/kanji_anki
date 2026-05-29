package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertRangeInfoEquals
import dev.bee.kanjianki.core.FrequencyRetentionRanges
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsFrequencyRangeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRankControlsAndWiresSlidersAndSave() {
        var saved = false
        var savedMinRank = ""
        var savedMaxRank = ""
        val selected = intArrayOf(100, 3000)

        composeRule.setContent {
            SettingsFrequencyRangePanel(
                model = SettingsFrequencyRangePanelModel(
                    title = SettingsTextCopy.frequencyRangeTitle(),
                    body = SettingsTextCopy.frequencyRangeBody(),
                    selectedRanks = selected,
                    minRankLabel = SettingsTextCopy.minRankLabel(),
                    initialMinRankText = "100",
                    maxRankLabel = SettingsTextCopy.maxRankLabel(),
                    initialMaxRankText = "3000",
                    minimumRankLabel = SettingsTextCopy.minimumRankLabel(),
                    maximumRankLabel = SettingsTextCopy.maximumRankLabel(),
                    saveLabel = SettingsTextCopy.saveFrequencyRangeLabel(),
                    onSave = SettingsFrequencyRangeSaveAction { minRankText, maxRankText ->
                        savedMinRank = minRankText
                        savedMaxRank = maxRankText
                        saved = true
                    }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeStatusText(100, 3000)).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MIN_RANK_INPUT).assertTextEquals("100")
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MAX_RANK_INPUT).assertTextEquals("3000")
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MIN_RANK_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                action(SettingsInputRules.rankSliderProgress(250).toFloat())
            }
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MAX_RANK_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                action(SettingsInputRules.rankSliderProgress(3500).toFloat())
        }

        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeStatusText(250, 3500)).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MIN_RANK_INPUT).assertTextEquals("250")
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MAX_RANK_INPUT).assertTextEquals("3500")
        composeRule.onNodeWithText(SettingsTextCopy.saveFrequencyRangeLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals(250, selected[0])
            assertEquals(3500, selected[1])
            assertEquals("250", savedMinRank)
            assertEquals("3500", savedMaxRank)
            assertTrue(saved)
        }
    }

    @Test
    fun rankTextInputsUpdateStatusSlidersAndSavedRange() {
        var saved = false
        var savedMinRank = ""
        var savedMaxRank = ""
        val selected = intArrayOf(100, 3000)

        composeRule.setContent {
            SettingsFrequencyRangePanel(
                model = SettingsFrequencyRangePanelModel(
                    title = SettingsTextCopy.frequencyRangeTitle(),
                    body = SettingsTextCopy.frequencyRangeBody(),
                    selectedRanks = selected,
                    minRankLabel = SettingsTextCopy.minRankLabel(),
                    initialMinRankText = "100",
                    maxRankLabel = SettingsTextCopy.maxRankLabel(),
                    initialMaxRankText = "3000",
                    minimumRankLabel = SettingsTextCopy.minimumRankLabel(),
                    maximumRankLabel = SettingsTextCopy.maximumRankLabel(),
                    saveLabel = SettingsTextCopy.saveFrequencyRangeLabel(),
                    onSave = SettingsFrequencyRangeSaveAction { minRankText, maxRankText ->
                        savedMinRank = minRankText
                        savedMaxRank = maxRankText
                        saved = true
                    }
                )
            )
        }

        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MIN_RANK_INPUT)
            .performTextReplacement("250")
        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeStatusText(250, 3000))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MIN_RANK_SLIDER)
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(
                    SettingsInputRules.rankSliderProgress(250).toFloat(),
                    0f..SettingsInputRules.rankSliderProgress(FrequencyRetentionRanges.MAX_RANK).toFloat()
                )
            )
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MAX_RANK_INPUT)
            .performTextReplacement("3500")
        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeStatusText(250, 3500))
            .assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.saveFrequencyRangeLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals(250, selected[0])
            assertEquals(3500, selected[1])
            assertEquals("250", savedMinRank)
            assertEquals("3500", savedMaxRank)
            assertTrue(saved)
        }
    }

    @Test
    fun rankTextInputsClampCrossedRangesBeforeSaving() {
        var savedMinRank = ""
        var savedMaxRank = ""
        val selected = intArrayOf(100, 3000)

        composeRule.setContent {
            SettingsFrequencyRangePanel(
                model = SettingsFrequencyRangePanelModel(
                    title = SettingsTextCopy.frequencyRangeTitle(),
                    body = SettingsTextCopy.frequencyRangeBody(),
                    selectedRanks = selected,
                    minRankLabel = SettingsTextCopy.minRankLabel(),
                    initialMinRankText = "100",
                    maxRankLabel = SettingsTextCopy.maxRankLabel(),
                    initialMaxRankText = "3000",
                    minimumRankLabel = SettingsTextCopy.minimumRankLabel(),
                    maximumRankLabel = SettingsTextCopy.maximumRankLabel(),
                    saveLabel = SettingsTextCopy.saveFrequencyRangeLabel(),
                    onSave = SettingsFrequencyRangeSaveAction { minRankText, maxRankText ->
                        savedMinRank = minRankText
                        savedMaxRank = maxRankText
                    }
                )
            )
        }

        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MIN_RANK_INPUT)
            .performTextReplacement("3500")
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MIN_RANK_INPUT).assertTextEquals("3000")
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MAX_RANK_INPUT).assertTextEquals("3000")
        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeStatusText(3000, 3000))
            .assertIsDisplayed()

        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MAX_RANK_INPUT)
            .performTextReplacement("2000")
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MIN_RANK_INPUT).assertTextEquals("3000")
        composeRule.onNodeWithTag(SettingsFrequencyRangeTestTags.MAX_RANK_INPUT).assertTextEquals("3000")
        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeStatusText(3000, 3000))
            .assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.saveFrequencyRangeLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals(3000, selected[0])
            assertEquals(3000, selected[1])
            assertEquals("3000", savedMinRank)
            assertEquals("3000", savedMaxRank)
        }
    }
}
