package dev.bee.kanjianki

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenCategoryNavigationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun categoryToggleCollapsesAndExpandsInPlace() {
        var toggleRuns = 0
        val panel = SettingsReferenceDataLinkModel(
            title = "Deep setting",
            body = "A nested Settings panel stays on the same composed page.",
            actionLabel = "Open",
            onAction = Runnable {},
        )
        val panelTag = settingsPanelTestTag(panel)
        val screen = settingsScreenModel(
            hero = SettingsAutomationHeroModel(
                cockpitLabel = "Settings",
                title = "Settings",
                body = "Tune Kani.",
                rows = emptyList(),
            ),
            categories = listOf(
                settingsCategorySectionModel(
                    sectionKey = "settings-study-behavior",
                    title = "Study settings",
                    summary = "Review pace and learning controls.",
                    iconRes = R.drawable.ic_study_24,
                    expanded = true,
                    onToggle = Runnable { toggleRuns += 1 },
                    panels = listOf(panel),
                ),
            ),
            onHome = Runnable {},
        )

        composeRule.setContent {
            SettingsScreen(screen)
        }

        composeRule.onNodeWithContentDescription("Collapse Study settings").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("settings-study-behavior"))
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        composeRule.onNodeWithText("1 card").assertIsDisplayed()
        composeRule.onNodeWithTag(panelTag).assertIsDisplayed()
        composeRule.onNodeWithText("Deep setting").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Collapse Study settings").performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleRuns)
        composeRule.onNodeWithContentDescription("Expand Study settings").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("settings-study-behavior"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        composeRule.onAllNodesWithTag(panelTag).assertCountEquals(0)
        composeRule.onAllNodesWithText("Deep setting").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Expand Study settings").performClick()
        composeRule.waitForIdle()

        assertEquals(2, toggleRuns)
        composeRule.onNodeWithContentDescription("Collapse Study settings").assertIsDisplayed()
        composeRule.onNodeWithTag(panelTag).assertIsDisplayed()
        composeRule.onNodeWithText("Deep setting").assertIsDisplayed()
    }
}
