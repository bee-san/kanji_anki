package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
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
        val selectedWorkload = intArrayOf(20)
        val selectedMax = intArrayOf(AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS)

        composeRule.setContent {
            SettingsWorkloadPanel(
                model = workloadModel(
                    autoMode = false,
                    selectedWorkload = selectedWorkload,
                    selectedMax = selectedMax,
                    onSaveWorkload = { saved = true },
                    onEnableAutomatic = { automatic = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.dailyWorkloadTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.workloadStatusText(20, AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsWorkloadTestTags.WORKLOAD_PERCENT_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                action(70f)
            }
        composeRule.onNodeWithTag(SettingsWorkloadTestTags.MAX_ITEMS_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                action(9f)
            }
        val expectedMax = AdaptiveLoadPlanner.normalizeMaxItems(9)
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
        val selectedMax = intArrayOf(AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS)

        composeRule.setContent {
            SettingsWorkloadPanel(
                model = workloadModel(
                    autoMode = true,
                    selectedWorkload = intArrayOf(20),
                    selectedMax = selectedMax,
                    onSaveMaximum = { savedMaximum = true },
                    onEnableManual = { manual = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.automaticWorkloadBody()).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsWorkloadTestTags.MAX_ITEMS_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                action(6f)
            }
        val expectedMax = AdaptiveLoadPlanner.normalizeMaxItems(6)
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
        onSaveMaximum: () -> Unit = {},
        onEnableManual: () -> Unit = {},
        onSaveWorkload: () -> Unit = {},
        onEnableAutomatic: () -> Unit = {},
    ): SettingsWorkloadPanelModel {
        return SettingsWorkloadPanelModel(
            title = SettingsTextCopy.dailyWorkloadTitle(),
            autoMode = autoMode,
            autoStatus = "Automatic workload: waiting for problem kanji",
            automaticBody = SettingsTextCopy.automaticWorkloadBody(),
            manualBody = SettingsTextCopy.manualWorkloadBody(),
            selectedWorkloadPercent = selectedWorkload,
            selectedMaxItems = selectedMax,
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
