package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
                    title = "Study behavior",
                    summary = "Review pace and learning controls.",
                    iconRes = R.drawable.ic_study_24,
                    expanded = true,
                    onToggle = Runnable { toggleRuns += 1 },
                    panels = listOf(
                        SettingsReferenceDataLinkModel(
                            title = "Deep setting",
                            body = "A nested Settings panel stays on the same composed page.",
                            actionLabel = "Open",
                            onAction = Runnable {},
                        ),
                    ),
                ),
            ),
            onHome = Runnable {},
        )

        composeRule.setContent {
            SettingsScreen(screen)
        }

        composeRule.onNodeWithContentDescription("Collapse Study behavior").assertIsDisplayed()
        composeRule.onNodeWithText("Deep setting").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Collapse Study behavior").performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleRuns)
        composeRule.onNodeWithContentDescription("Expand Study behavior").assertIsDisplayed()
        composeRule.onAllNodesWithText("Deep setting").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Expand Study behavior").performClick()
        composeRule.waitForIdle()

        assertEquals(2, toggleRuns)
        composeRule.onNodeWithContentDescription("Collapse Study behavior").assertIsDisplayed()
        composeRule.onNodeWithText("Deep setting").assertIsDisplayed()
    }
}
