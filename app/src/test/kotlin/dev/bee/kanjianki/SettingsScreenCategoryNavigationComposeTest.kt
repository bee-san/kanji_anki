package dev.bee.kanjianki

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.bee.kanjianki.core.SettingsTextCopy

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
        composeRule.onNodeWithTag(SETTINGS_SCREEN_BOTTOM_SPACER_TAG)
            .assertExists()

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

    @Test
    fun categoryHeaderTitleIsClickable() {
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
                    title = "Study settings",
                    summary = "Review pace and learning controls.",
                    iconRes = R.drawable.ic_study_24,
                    expanded = true,
                    onToggle = Runnable { toggleRuns += 1 },
                    panels = emptyList(),
                ),
            ),
            onHome = Runnable {},
        )

        composeRule.setContent {
            SettingsScreen(screen)
        }

        composeRule.onNodeWithText("Study settings").assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleRuns)
        composeRule.onNodeWithContentDescription("Expand Study settings").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("settings-study-behavior"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
    }

    @Test
    fun studySettingsPanelsRenderInsideScrollingShell() {
        var toggleRuns = 0
        val studySort = SettingsNewCardSortPanelModel(
            title = "New card sort",
            body = "Choose the order used for new cards.",
            initialMode = "due",
            options = listOf(
                SettingsNewCardSortOptionModel(
                    label = "Due order",
                    mode = "due",
                    description = "Use due-date order.",
                ),
                SettingsNewCardSortOptionModel(
                    label = "Random order",
                    mode = "random",
                    description = "Use a random order.",
                ),
            ),
            saveLabel = "Save sort",
            previewRowsByMode = mapOf(
                "due" to listOf(
                    SettingsNewCardSortPreviewRowModel(
                        kanji = "漢",
                        primaryMeaning = "kan",
                        scoreLabel = "A",
                    ),
                ),
            ),
            onSave = SettingsNewCardSortSaver {},
        )
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
                    expanded = false,
                    onToggle = Runnable { toggleRuns += 1 },
                    panels = listOf(studySort),
                ),
            ),
            onHome = Runnable {},
        )

        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsScreen(screen)
            }
        }

        composeRule.onNodeWithText("Study settings").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleRuns)
        composeRule.onNodeWithText("New card sort").assertIsDisplayed()
        composeRule.onNodeWithText("Choose the order used for new cards.").assertIsDisplayed()

        composeRule.onNodeWithText("Study settings").performClick()
        composeRule.waitForIdle()

        assertEquals(2, toggleRuns)
        composeRule.onAllNodesWithText("New card sort").assertCountEquals(0)
        composeRule.onAllNodesWithText("Choose the order used for new cards.").assertCountEquals(0)
    }

    @Test
    fun siblingSettingsCategoriesToggleInsideScrollingShell() {
        val cases = listOf(
            CategoryReachabilityCase(
                sectionKey = "settings-anki-source",
                title = SettingsTextCopy.settingsAnkiSourceTitle(),
                summary = SettingsTextCopy.settingsAnkiSourceBody(),
                panelTitle = "Import & sync detail",
            ),
            CategoryReachabilityCase(
                sectionKey = "settings-automation",
                title = SettingsTextCopy.settingsAutomationTitle(),
                summary = SettingsTextCopy.settingsAutomationBody(),
                panelTitle = "Automation detail",
            ),
            CategoryReachabilityCase(
                sectionKey = "settings-appearance",
                title = SettingsTextCopy.settingsAppearanceTitle(),
                summary = SettingsTextCopy.settingsAppearanceBody(),
                panelTitle = "Appearance detail",
            ),
            CategoryReachabilityCase(
                sectionKey = "settings-reference-data",
                title = SettingsTextCopy.settingsReferenceDataTitle(),
                summary = SettingsTextCopy.settingsReferenceDataBody(),
                panelTitle = "Display & data detail",
            ),
        )
        val toggleRuns = mutableMapOf<String, Int>()
        val screen = settingsScreenModel(
            hero = SettingsAutomationHeroModel(
                cockpitLabel = "Settings",
                title = "Settings",
                body = "Tune Kani.",
                rows = emptyList(),
            ),
            categories = cases.map { case ->
                settingsCategorySectionModel(
                    sectionKey = case.sectionKey,
                    title = case.title,
                    summary = case.summary,
                    iconRes = R.drawable.ic_book_24,
                    expanded = false,
                    onToggle = Runnable {
                        toggleRuns[case.sectionKey] = toggleRuns.getOrDefault(case.sectionKey, 0) + 1
                    },
                    panels = listOf(
                        SettingsReferenceDataLinkModel(
                            title = case.panelTitle,
                            body = "A nested panel for ${case.title} stays on the same composed page.",
                            actionLabel = "Open ${case.title}",
                            onAction = Runnable {},
                        )
                    ),
                )
            },
            onHome = Runnable {},
        )

        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsScreen(screen)
            }
        }

        cases.forEach { case ->
            composeRule.onNodeWithTag(settingsCategoryHeaderTestTag(case.sectionKey))
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            composeRule.onNodeWithText(case.title).performScrollTo().assertIsDisplayed().performClick()
            composeRule.waitForIdle()

            assertEquals(1, toggleRuns.getOrDefault(case.sectionKey, 0))
            composeRule.onNodeWithTag(settingsCategoryHeaderTestTag(case.sectionKey))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
            composeRule.onNodeWithText(case.panelTitle).performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithText(case.title).performClick()
            composeRule.waitForIdle()

            assertEquals(2, toggleRuns.getOrDefault(case.sectionKey, 0))
            composeRule.onNodeWithTag(settingsCategoryHeaderTestTag(case.sectionKey))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            composeRule.onAllNodesWithText(case.panelTitle).assertCountEquals(0)
        }
    }
}

private data class CategoryReachabilityCase(
    val sectionKey: String,
    val title: String,
    val summary: String,
    val panelTitle: String,
)
