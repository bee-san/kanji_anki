package dev.bee.kanjianki

import android.content.Context
import android.widget.EditText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val promotionDays = EditText(context).apply { setText("30") }
        val failStreak = EditText(context).apply { setText("2") }

        composeRule.setContent {
            SettingsLadderThresholdPanel(
                model = SettingsLadderThresholdPanelModel(
                    title = SettingsTextCopy.ladderThresholdsTitle(),
                    body = SettingsTextCopy.ladderThresholdsBody(),
                    promotionDaysLabel = SettingsTextCopy.fsrsDaysToGoUpLabel(),
                    promotionDaysInput = promotionDays,
                    failStreakLabel = SettingsTextCopy.failsToGoDownLabel(),
                    failStreakInput = failStreak,
                    defaultsLabel = SettingsTextCopy.useDefaultLadderThresholdsLabel(),
                    saveLabel = SettingsTextCopy.saveLadderThresholdsLabel(),
                    onUseDefaults = SettingsLadderThresholdAction {
                        promotionDays.setText(RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString())
                        failStreak.setText(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString())
                    },
                    onSave = SettingsLadderThresholdAction { saved = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.ladderThresholdsTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.ladderThresholdsBody()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.fsrsDaysToGoUpLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.failsToGoDownLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.useDefaultLadderThresholdsLabel()).performClick()
        composeRule.runOnIdle {
            assertEquals(RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString(), promotionDays.text.toString())
            assertEquals(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString(), failStreak.text.toString())
        }

        composeRule.onNodeWithText(SettingsTextCopy.saveLadderThresholdsLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(saved)
        }
    }
}
