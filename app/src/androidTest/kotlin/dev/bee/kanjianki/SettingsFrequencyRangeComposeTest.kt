package dev.bee.kanjianki

import android.content.Context
import android.widget.EditText
import android.widget.SeekBar
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selected = intArrayOf(100, 3000)
        val minInput = EditText(context).apply { setText("100") }
        val maxInput = EditText(context).apply { setText("3000") }
        val minSlider = SeekBar(context)
        val maxSlider = SeekBar(context)

        composeRule.setContent {
            SettingsFrequencyRangePanel(
                model = SettingsFrequencyRangePanelModel(
                    title = SettingsTextCopy.frequencyRangeTitle(),
                    body = SettingsTextCopy.frequencyRangeBody(),
                    selectedRanks = selected,
                    minRankLabel = SettingsTextCopy.minRankLabel(),
                    minRankInput = minInput,
                    maxRankLabel = SettingsTextCopy.maxRankLabel(),
                    maxRankInput = maxInput,
                    minimumRankLabel = SettingsTextCopy.minimumRankLabel(),
                    minRankSlider = minSlider,
                    maximumRankLabel = SettingsTextCopy.maximumRankLabel(),
                    maxRankSlider = maxSlider,
                    saveLabel = SettingsTextCopy.saveFrequencyRangeLabel(),
                    onSave = SettingsFrequencyRangeAction { saved = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeStatusText(100, 3000)).assertIsDisplayed()
        composeRule.runOnIdle {
            minSlider.progress = SettingsInputRules.rankSliderProgress(250)
            maxSlider.progress = SettingsInputRules.rankSliderProgress(3500)
        }

        composeRule.onNodeWithText(SettingsTextCopy.frequencyRangeStatusText(250, 3500)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.saveFrequencyRangeLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals(250, selected[0])
            assertEquals(3500, selected[1])
            assertEquals("250", minInput.text.toString())
            assertEquals("3500", maxInput.text.toString())
            assertTrue(saved)
        }
    }
}
