package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun hubCardsExposeDescriptionsAndInvokeOpenCallbacks() {
        var openRuns = 0
        val screen = SettingsScreenModel(
            homeLabel = "Home",
            onHome = Runnable {},
            title = "Settings",
            cards = listOf(
                SettingsHubCardModel(
                    routeKey = MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE,
                    title = "Study settings",
                    summary = "Review pace and learning controls.",
                    iconRes = R.drawable.ic_study_24,
                    panelCount = "8 cards",
                    contentDescription = "Open Study settings",
                    onOpen = Runnable { openRuns += 1 },
                ),
            ),
        )

        composeRule.setContent { SettingsScreen(screen) }

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Study settings").assertIsDisplayed()
        composeRule.onNodeWithText("8 cards").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open Study settings").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsHubCardTestTag(MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE))
            .assertIsDisplayed()
            .performClick()

        assertTrue(openRuns == 1)
    }

    @Test
    fun submenuBackButtonReturnsToSettingsAndKeepsContentVisible() {
        var homeClicked = false
        var backClicked = false
        val panel = SettingsReferenceDataLinkModel(
            title = "Data licenses",
            body = "Dictionary, stroke, and font attributions.",
            actionLabel = SettingsTextCopy.openDataLicensesLabel(),
            onAction = Runnable {},
        )

        composeRule.setContent {
            SettingsSubmenuScreen(
                model = SettingsSubmenuScreenModel(
                    homeLabel = "Home",
                    onHome = Runnable { homeClicked = true },
                    backLabel = SettingsTextCopy.backToSettingsLabel(),
                    onBack = Runnable { backClicked = true },
                    title = "Display & data",
                    body = "Manage dictionaries and credits.",
                    panels = listOf(panel),
                ),
            )
        }

        composeRule.onNodeWithText("Home").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Display & data").assertIsDisplayed()
        composeRule.onNodeWithText("Manage dictionaries and credits.").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsPanelTestTag(panel)).assertIsDisplayed()

        assertTrue(homeClicked)
        assertTrue(backClicked)
    }

    @Test
    fun studySettingsPanelsRenderInsideScrollingShell() {
        var toggleRuns = 0
        var expanded by mutableStateOf(false)
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

        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsCategorySection(
                    settingsCategorySectionModel(
                        sectionKey = "settings-study-behavior",
                        title = "Study settings",
                        summary = "Review pace and learning controls.",
                        iconRes = R.drawable.ic_study_24,
                        expanded = expanded,
                        onToggle = Runnable {
                            toggleRuns += 1
                            expanded = !expanded
                        },
                        panels = listOf(studySort),
                    ),
                )
            }
        }

        val headerTag = settingsCategoryHeaderTestTag("settings-study-behavior")
        composeRule.onNodeWithTag(headerTag)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleRuns)
        composeRule.onNodeWithTag(headerTag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        composeRule.onNodeWithTag(settingsPanelTestTag(studySort)).assertIsDisplayed()
        composeRule.onNodeWithText("New card sort").assertIsDisplayed()
        composeRule.onNodeWithText("Choose the order used for new cards.").assertIsDisplayed()

        composeRule.onNodeWithTag(headerTag).performClick()
        composeRule.waitForIdle()

        assertEquals(2, toggleRuns)
        composeRule.onNodeWithTag(headerTag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        composeRule.onAllNodesWithText("New card sort").assertCountEquals(0)
        composeRule.onAllNodesWithText("Choose the order used for new cards.").assertCountEquals(0)
    }

    @Test
    fun siblingSettingsCategoriesToggleInsideScrollingShell() {
        var importExpanded by mutableStateOf(false)
        var appearanceExpanded by mutableStateOf(false)
        var importToggleRuns = 0
        var appearanceToggleRuns = 0
        val importPanel = SettingsReferenceDataLinkModel(
            title = "Data licenses",
            body = "Dictionary, stroke, and font attributions.",
            actionLabel = SettingsTextCopy.openDataLicensesLabel(),
            onAction = Runnable {},
        )
        val appearancePanel = SettingsThemePanelModels.themeSettingsPanelModel(
            currentChoice = KaniThemeChoice.SYSTEM,
            onSelectChoice = {},
        )

        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsCategorySection(
                    settingsCategorySectionModel(
                        sectionKey = "settings-reference-data",
                        title = SettingsTextCopy.settingsReferenceDataTitle(),
                        summary = SettingsTextCopy.settingsReferenceDataBody(),
                        iconRes = R.drawable.ic_book_24,
                        expanded = importExpanded,
                        onToggle = Runnable {
                            importToggleRuns += 1
                            importExpanded = !importExpanded
                        },
                        panels = listOf(importPanel),
                    ),
                )
                SettingsCategorySection(
                    settingsCategorySectionModel(
                        sectionKey = "settings-appearance",
                        title = SettingsTextCopy.settingsAppearanceTitle(),
                        summary = SettingsTextCopy.settingsAppearanceBody(),
                        iconRes = R.drawable.ic_book_24,
                        expanded = appearanceExpanded,
                        onToggle = Runnable {
                            appearanceToggleRuns += 1
                            appearanceExpanded = !appearanceExpanded
                        },
                        panels = listOf(appearancePanel),
                    ),
                )
            }
        }

        val importHeader = settingsCategoryHeaderTestTag("settings-reference-data")
        val appearanceHeader = settingsCategoryHeaderTestTag("settings-appearance")

        composeRule.onNodeWithTag(importHeader)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, importToggleRuns)
        composeRule.onNodeWithTag(importHeader)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        composeRule.onNodeWithTag(settingsPanelTestTag(importPanel)).assertIsDisplayed()

        composeRule.onNodeWithTag(appearanceHeader)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, appearanceToggleRuns)
        composeRule.onNodeWithTag(appearanceHeader)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        composeRule.onNodeWithTag(settingsPanelTestTag(appearancePanel)).assertIsDisplayed()

        composeRule.onNodeWithTag(importHeader).performClick()
        composeRule.waitForIdle()
        assertEquals(2, importToggleRuns)
        composeRule.onNodeWithTag(importHeader)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        composeRule.onAllNodesWithText("Data licenses").assertCountEquals(0)

        composeRule.onNodeWithTag(appearanceHeader).performClick()
        composeRule.waitForIdle()
        assertEquals(2, appearanceToggleRuns)
        composeRule.onNodeWithTag(appearanceHeader)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        composeRule.onAllNodesWithTag(settingsPanelTestTag(appearancePanel)).assertCountEquals(0)
    }
}
