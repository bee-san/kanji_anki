package dev.bee.kanjianki

import android.content.Context
import android.widget.SeekBar
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsWorkloadComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersManualWorkloadAndWiresSlidersAndActions() {
        var saved = false
        var automatic = false
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selectedWorkload = intArrayOf(20)
        val selectedMax = intArrayOf(AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS)
        val workloadSlider = SeekBar(context)
        val maxItemsSlider = SeekBar(context)

        composeRule.setContent {
            SettingsWorkloadPanel(
                model = workloadModel(
                    autoMode = false,
                    selectedWorkload = selectedWorkload,
                    selectedMax = selectedMax,
                    workloadSlider = workloadSlider,
                    maxItemsSlider = maxItemsSlider,
                    onSaveWorkload = { saved = true },
                    onEnableAutomatic = { automatic = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.dailyWorkloadTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.workloadStatusText(20, AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            workloadSlider.progress = 73
            maxItemsSlider.progress = 4
        }
        val expectedMax = AdaptiveLoadPlanner.normalizeMaxItems(AdaptiveLoadPlanner.MIN_MAX_ITEMS + 4)
        composeRule.onNodeWithText(SettingsTextCopy.workloadStatusText(70, expectedMax)).assertIsDisplayed()

        composeRule.onNodeWithText(SettingsTextCopy.saveWorkloadLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.automaticParetoLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals(70, selectedWorkload[0])
            assertEquals(expectedMax, selectedMax[0])
            assertTrue(saved)
            assertTrue(automatic)
        }
    }

    @Test
    fun rendersAutomaticWorkloadAndWiresActions() {
        var savedMaximum = false
        var manual = false
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selectedMax = intArrayOf(AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS)
        val maxItemsSlider = SeekBar(context)

        composeRule.setContent {
            SettingsWorkloadPanel(
                model = workloadModel(
                    autoMode = true,
                    selectedWorkload = intArrayOf(20),
                    selectedMax = selectedMax,
                    workloadSlider = SeekBar(context),
                    maxItemsSlider = maxItemsSlider,
                    onSaveMaximum = { savedMaximum = true },
                    onEnableManual = { manual = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.automaticWorkloadBody()).assertIsDisplayed()
        composeRule.runOnIdle {
            maxItemsSlider.progress = 5
        }
        val expectedMax = AdaptiveLoadPlanner.normalizeMaxItems(AdaptiveLoadPlanner.MIN_MAX_ITEMS + 5)
        composeRule.onNodeWithText(SettingsTextCopy.maxItemsStatusText(expectedMax)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.saveMaximumLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.manualWorkloadLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals(expectedMax, selectedMax[0])
            assertTrue(savedMaximum)
            assertTrue(manual)
        }
    }

    private fun workloadModel(
        autoMode: Boolean,
        selectedWorkload: IntArray,
        selectedMax: IntArray,
        workloadSlider: SeekBar,
        maxItemsSlider: SeekBar,
        onSaveMaximum: () -> Unit = {},
        onEnableManual: () -> Unit = {},
        onSaveWorkload: () -> Unit = {},
        onEnableAutomatic: () -> Unit = {},
    ): SettingsWorkloadPanelModel {
        return SettingsWorkloadPanelModel(
            title = SettingsTextCopy.dailyWorkloadTitle(),
            autoMode = autoMode,
            autoStatus = "Auto Pareto: waiting for problem kanji",
            automaticBody = SettingsTextCopy.automaticWorkloadBody(),
            manualBody = SettingsTextCopy.manualWorkloadBody(),
            selectedWorkloadPercent = selectedWorkload,
            selectedMaxItems = selectedMax,
            workloadSlider = workloadSlider,
            maxItemsSlider = maxItemsSlider,
            scaleLabels = SettingsTextCopy.workloadScaleLabels().toList(),
            saveMaximumLabel = SettingsTextCopy.saveMaximumLabel(),
            manualWorkloadLabel = SettingsTextCopy.manualWorkloadLabel(),
            saveWorkloadLabel = SettingsTextCopy.saveWorkloadLabel(),
            automaticParetoLabel = SettingsTextCopy.automaticParetoLabel(),
            onSaveMaximum = SettingsWorkloadAction { onSaveMaximum() },
            onEnableManual = SettingsWorkloadAction { onEnableManual() },
            onSaveWorkload = SettingsWorkloadAction { onSaveWorkload() },
            onEnableAutomatic = SettingsWorkloadAction { onEnableAutomatic() }
        )
    }
}
