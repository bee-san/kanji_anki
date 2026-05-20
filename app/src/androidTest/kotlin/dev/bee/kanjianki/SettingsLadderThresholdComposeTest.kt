package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsLadderThresholdComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersLadderThresholdCopyAndWiresDefaultsAndSave() {
        var saved = false
        var savedPromotionDays = ""
        var savedFailStreak = ""

        composeRule.setContent {
            SettingsLadderThresholdPanel(
                model = SettingsLadderThresholdPanelModel(
                    title = SettingsTextCopy.ladderThresholdsTitle(),
                    body = SettingsTextCopy.ladderThresholdsBody(),
                    promotionDaysLabel = SettingsTextCopy.fsrsDaysToGoUpLabel(),
                    initialPromotionDaysText = "30",
                    failStreakLabel = SettingsTextCopy.failsToGoDownLabel(),
                    initialFailStreakText = "2",
                    defaultPromotionDaysText = RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString(),
                    defaultFailStreakText = RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString(),
                    defaultsLabel = SettingsTextCopy.useDefaultLadderThresholdsLabel(),
                    saveLabel = SettingsTextCopy.saveLadderThresholdsLabel(),
                    onSave = SettingsLadderThresholdSaveAction { promotionDaysText, failStreakText ->
                        savedPromotionDays = promotionDaysText
                        savedFailStreak = failStreakText
                        saved = true
                    }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.ladderThresholdsTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.ladderThresholdsBody()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.fsrsDaysToGoUpLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.failsToGoDownLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsLadderThresholdTestTags.PROMOTION_DAYS_INPUT).performTextReplacement("99")
        composeRule.onNodeWithTag(SettingsLadderThresholdTestTags.FAIL_STREAK_INPUT).performTextReplacement("7")
        composeRule.onNodeWithText(SettingsTextCopy.useDefaultLadderThresholdsLabel()).performClick()
        composeRule.onNodeWithTag(SettingsLadderThresholdTestTags.PROMOTION_DAYS_INPUT)
            .assertTextEquals(RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString())
        composeRule.onNodeWithTag(SettingsLadderThresholdTestTags.FAIL_STREAK_INPUT)
            .assertTextEquals(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString())

        composeRule.onNodeWithText(SettingsTextCopy.saveLadderThresholdsLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(saved)
            assertEquals(RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString(), savedPromotionDays)
            assertEquals(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString(), savedFailStreak)
        }
    }
}
