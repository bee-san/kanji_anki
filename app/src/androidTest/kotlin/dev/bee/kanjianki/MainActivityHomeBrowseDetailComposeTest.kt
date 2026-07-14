package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityHomeBrowseDetailComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTimelineCardsAndEmptyState() {
        val statusText = "Recovered after today's sync"

        composeRule.setContent {
            RecoveryTimelinePanels(
                model = MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel(
                    "Recovery timeline",
                    statusText,
                    0xFF00AEB5.toInt(),
                    "Needs 2 mature cards to fully support this kanji.",
                    listOf(
                        MainActivityHomeBrowseDetail.BrowseTimelineEventModel(
                            "Today",
                            "Synced from AnkiDroid",
                            "The kanji is still active.",
                            "Source: active dashboard row",
                            0xFF6E5CE6.toInt()
                        )
                    ),
                    null
                )
            )
        }

        composeRule.onAllNodesWithText("Recovery timeline").assertCountEquals(1)
        composeRule.onAllNodesWithText(statusText).assertCountEquals(1)
        composeRule.onAllNodesWithText("Needs 2 mature cards to fully support this kanji.").assertCountEquals(1)
        composeRule.onAllNodesWithText("Today").assertCountEquals(1)
        composeRule.onAllNodesWithText("Synced from AnkiDroid").assertCountEquals(1)
        composeRule.onAllNodesWithText("The kanji is still active.").assertCountEquals(1)
        composeRule.onAllNodesWithText("Source: active dashboard row").assertCountEquals(1)
    }

    @Test
    fun rendersBrowseRowsAndInvokesCallbacks() {
        var searched = ""
        var homeClicked = false
        var clickedKanji = ""
        var toggledFilterQuery = ""
        var selectAllClicked = false
        var deselectAllClicked = false
        val studiedChanges = mutableListOf<Pair<String, Boolean>>()

        composeRule.setContent {
            BrowseScreen(
                model = BrowseScreenModel(
                    initialQuery = " 裂 ",
                    resultHeading = "2 kanji",
                    similarFilterActive = false,
                    studySelectionSummary = HomeTextCopy.browseStudySelectionSummary(1, 2),
                    onToggleSimilarFilter = { toggledFilterQuery = it },
                    onSelectAllStudied = { selectAllClicked = true },
                    onDeselectAllStudied = { deselectAllClicked = true },
                    rows = listOf(
                        BrowseKanjiRowModel(
                            kanji = "裂",
                            meaning = "split",
                            readings = "レツ",
                            summary = "2 local sources · 1 example",
                            contentDescription = browseKanjiRowDescription(
                                kanji = "裂",
                                meaning = "split",
                                readings = "レツ",
                                summary = "2 local sources · 1 example",
                                studied = false,
                                suspended = true,
                            ),
                            suspended = true,
                            studied = false,
                            onClick = { clickedKanji = "裂" },
                            onStudiedChange = { studiedChanges.add("裂" to it) }
                        ),
                        BrowseKanjiRowModel(
                            kanji = "謎",
                            meaning = "Meaning not stored yet",
                            readings = "",
                            summary = "0 local sources · 1 example",
                            contentDescription = browseKanjiRowDescription(
                                kanji = "謎",
                                meaning = "Meaning not stored yet",
                                readings = "",
                                summary = "0 local sources · 1 example",
                                studied = true,
                                suspended = false,
                            ),
                            suspended = false,
                            studied = true,
                            onClick = { clickedKanji = "謎" },
                            onStudiedChange = { studiedChanges.add("謎" to it) }
                        )
                    ),
                    onHome = { homeClicked = true },
                    onSearch = { searched = it }
                )
            )
        }

        composeRule.onAllNodesWithText("Browse Kanji").assertCountEquals(1)
        composeRule.onAllNodesWithText("2 kanji").assertCountEquals(1)
        composeRule.onAllNodesWithText("split").assertCountEquals(1)
        composeRule.onAllNodesWithText("レツ").assertCountEquals(1)
        composeRule.onAllNodesWithText("SUSPENDED").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription(
            browseKanjiRowDescription(
                kanji = "裂",
                meaning = "split",
                readings = "レツ",
                summary = "2 local sources · 1 example",
                studied = false,
                suspended = true,
            )
        ).assertCountEquals(1)
        composeRule.onAllNodesWithText("Meaning not stored yet").assertCountEquals(1)
        composeRule.onAllNodesWithText(HomeTextCopy.browseSimilarFilterLabel()).assertCountEquals(1)
        composeRule.onAllNodesWithText(HomeTextCopy.browseStudySelectionSummary(1, 2)).assertCountEquals(1)
        composeRule.onNodeWithTag(browseKanjiStudiedToggleTestTag("裂")).assertIsOff()
        composeRule.onNodeWithTag(browseKanjiStudiedToggleTestTag("謎")).assertIsOn()

        composeRule.onNodeWithText("Search").assertIsEnabled().performClick()
        assertEquals(" 裂 ", searched)

        composeRule.onNodeWithTag(browseSimilarFilterTestTag()).performClick()
        assertEquals(" 裂 ", toggledFilterQuery)

        composeRule.onNodeWithTag(browseSelectAllStudiedTestTag()).performClick()
        composeRule.onNodeWithTag(browseDeselectAllStudiedTestTag()).performClick()
        assertTrue(selectAllClicked)
        assertTrue(deselectAllClicked)

        composeRule.onNodeWithTag(browseKanjiStudiedToggleTestTag("裂")).performClick()
        assertEquals("裂" to true, studiedChanges.last())

        composeRule.onNodeWithTag(browseKanjiRowTestTag("裂")).assertIsEnabled().performClick()
        assertEquals("裂", clickedKanji)

        composeRule.onNodeWithText("Home").assertIsEnabled().performClick()
        assertTrue(homeClicked)
    }

    @Test
    fun rendersBrowseEmptyStateWithoutRows() {
        composeRule.setContent {
            BrowseScreen(
                model = BrowseScreenModel(
                    initialQuery = "missing",
                    resultHeading = "No matches",
                    rows = emptyList(),
                    onHome = {},
                    onSearch = {}
                )
            )
        }

        composeRule.onAllNodesWithText("No matches").assertCountEquals(1)
        composeRule.onAllNodesWithText("No local kanji found").assertCountEquals(1)
        composeRule.onAllNodesWithText("Sync AnkiDroid first, or try a different search.").assertCountEquals(1)
        composeRule.onAllNodesWithText("SUSPENDED").assertCountEquals(0)
    }

    @Test
    fun rendersDetailInfoPanelsAndExampleCards() {
        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                BrowseDetailInfoPanel(
                    model = BrowseDetailPanelModel(
                        title = "",
                        lines = listOf("Active practice evidence.", "Anki search: deck:Japanese"),
                        color = 0xFF6E5CE6.toInt(),
                        style = BrowseDetailPanelStyle.BAND
                    )
                )
                BrowseDetailInfoPanel(
                    model = BrowseDetailPanelModel(
                        title = "Local records",
                        lines = listOf("1 source · 2 examples", "Anki search: kanji:語"),
                        color = 0xFFC9F5F7.toInt(),
                        style = BrowseDetailPanelStyle.CARD
                    )
                )
                BrowseExampleCard(
                    model = BrowseExampleCardModel(
                        sourceLabel = "ACTIVE",
                        expression = "活動語  カツドウゴ",
                        sentence = "活動語を見た。",
                        meaning = "active word",
                        color = 0xFF00AEB5.toInt()
                    )
                )
            }
        }

        composeRule.onAllNodesWithText("Active practice evidence.").assertCountEquals(1)
        composeRule.onAllNodesWithText("Anki search: deck:Japanese").assertCountEquals(1)
        composeRule.onAllNodesWithText("Local records").assertCountEquals(1)
        composeRule.onAllNodesWithText("1 source · 2 examples").assertCountEquals(1)
        composeRule.onAllNodesWithText("Anki search: kanji:語").assertCountEquals(1)
        composeRule.onAllNodesWithText("ACTIVE").assertCountEquals(1)
        composeRule.onAllNodesWithText("活動語  カツドウゴ").assertCountEquals(1)
        composeRule.onAllNodesWithText("活動語を見た。").assertCountEquals(1)
        composeRule.onAllNodesWithText("active word").assertCountEquals(1)
    }

    @Test
    fun rendersDetailHeroIdentityAndActions() {
        var backClicked = false
        var reviewClicked = false
        var copyClicked = false
        var suspendClicked = false

        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                BrowseDetailHero(
                    model = BrowseDetailHeroModel(
                        kanji = "裂",
                        navigationLabel = "Back to Browse",
                        onNavigate = Runnable { backClicked = true }
                    )
                )
                BrowseDetailIdentity(
                    model = BrowseDetailIdentityModel(
                        title = "split",
                        reading = "レツ",
                        stateBadges = listOf(BrowseStateBadgeModel("SUSPENDED", 0xFFFF4C76.toInt()))
                    )
                )
                BrowseDetailActions(
                    model = BrowseDetailActionsModel(
                        reviewLabel = "Review now",
                        onReview = Runnable { reviewClicked = true },
                        copyLabel = "Copy search",
                        copiedLabel = "Copied",
                        onCopy = Runnable { copyClicked = true },
                        suspendLabel = "Unsuspend locally",
                        onSuspend = Runnable { suspendClicked = true }
                    )
                )
            }
        }

        composeRule.onAllNodesWithText("裂").assertCountEquals(1)
        composeRule.onAllNodesWithText("split").assertCountEquals(1)
        composeRule.onAllNodesWithText("レツ").assertCountEquals(1)
        composeRule.onAllNodesWithText("SUSPENDED").assertCountEquals(1)

        composeRule.onNodeWithText("Back to Browse").performClick()
        composeRule.onNodeWithText("Review now").performClick()
        composeRule.onNodeWithText("Copy search").performClick()
        composeRule.onAllNodesWithText("Copied").assertCountEquals(1)
        composeRule.onNodeWithText("Unsuspend locally").performClick()

        assertTrue(backClicked)
        assertTrue(reviewClicked)
        assertTrue(copyClicked)
        assertTrue(suspendClicked)
    }

    @Test
    fun mnemonicEditorIsAccessibleAndSavesNormalizedMultilineTextOrBlankDeletion() {
        val savedNotes = mutableListOf<String>()
        val stuckHelper = HomeTextCopy.stuckChipHint()

        composeRule.setContent {
            BrowseMnemonicNoteEditor(
                BrowseMnemonicNoteModel(
                    title = "My mnemonic",
                    fieldLabel = "Mnemonic note",
                    helper = stuckHelper,
                    initialNote = "shell\nstory",
                    saveLabel = "Save mnemonic",
                    onSave = savedNotes::add,
                )
            )
        }

        composeRule.onNodeWithTag(BrowseMnemonicNoteTestTags.INPUT).assertTextContains("shell\nstory")
        composeRule.onAllNodesWithContentDescription("Mnemonic note").assertCountEquals(1)
        composeRule.onAllNodesWithText(stuckHelper).assertCountEquals(1)

        composeRule.onNodeWithTag(BrowseMnemonicNoteTestTags.INPUT)
            .performTextReplacement("  splits open\n  like a shell  ")
        composeRule.onNodeWithTag(BrowseMnemonicNoteTestTags.SAVE).assertIsEnabled().performClick()
        assertEquals("splits open\n  like a shell", savedNotes.last())

        composeRule.onNodeWithTag(BrowseMnemonicNoteTestTags.INPUT).performTextReplacement(" \n\t ")
        composeRule.onNodeWithTag(BrowseMnemonicNoteTestTags.SAVE).performClick()
        assertEquals("", savedNotes.last())
    }

    @Test
    fun rendersFullDetailScreenAndMissingState() {
        var homeClicked = false
        var reviewClicked = false

        composeRule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                BrowseDetailScreen(
                    model = BrowseDetailScreenModel(
                        hero = BrowseDetailHeroModel(
                            kanji = "裂",
                            navigationLabel = "Back to Browse",
                            onNavigate = Runnable { homeClicked = true }
                        ),
                        identity = BrowseDetailIdentityModel(
                            title = "split",
                            reading = "レツ",
                            stateBadges = emptyList()
                        ),
                        strokeOrder = null,
                        reason = BrowseDetailPanelModel(
                            title = "",
                            lines = listOf("Active practice evidence."),
                            color = 0xFF6E5CE6.toInt(),
                            style = BrowseDetailPanelStyle.BAND
                        ),
                        localInventory = BrowseDetailPanelModel(
                            title = "Local records",
                            lines = listOf("1 source · 1 example"),
                            color = 0xFFC9F5F7.toInt(),
                            style = BrowseDetailPanelStyle.CARD
                        ),
                        mnemonicNote = BrowseMnemonicNoteModel(
                            title = "My mnemonic",
                            fieldLabel = "Mnemonic note",
                            helper = "Write a story that helps this kanji stick.",
                            initialNote = "A shell splits open.",
                            saveLabel = "Save mnemonic",
                            onSave = {},
                        ),
                        actions = BrowseDetailActionsModel(
                            reviewLabel = "Review now",
                            onReview = Runnable { reviewClicked = true },
                            copyLabel = null,
                            copiedLabel = "Copied",
                            onCopy = null,
                            suspendLabel = "Suspend locally",
                            onSuspend = Runnable {}
                        ),
                        timeline = MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel(
                            "Recovery timeline",
                            "Active repair",
                            0xFFFF4C76.toInt(),
                            "Needs 2 mature cards to fully support this kanji.",
                            emptyList(),
                            "No timeline events yet."
                        ),
                        examplesTitle = "Examples",
                        examples = listOf(
                            BrowseExampleCardModel(
                                sourceLabel = "ACTIVE",
                                expression = "裂語  レツゴ",
                                sentence = "裂語を見た。",
                                meaning = "split word",
                                color = 0xFF00AEB5.toInt()
                            )
                        )
                    )
                )
                BrowseDetailMissing(
                    BrowseDetailMissingModel(
                        homeLabel = "Home",
                        onHome = Runnable {},
                        title = "Kanji not found",
                        body = "This kanji is not in local history."
                    )
                )
            }
        }

        composeRule.onAllNodesWithText("裂").assertCountEquals(1)
        composeRule.onAllNodesWithText("Local records").assertCountEquals(1)
        composeRule.onAllNodesWithText("My mnemonic").assertCountEquals(1)
        composeRule.onNodeWithTag(BrowseMnemonicNoteTestTags.INPUT).assertTextContains("A shell splits open.")
        composeRule.onAllNodesWithText("Recovery timeline").assertCountEquals(1)
        composeRule.onAllNodesWithText("Examples").assertCountEquals(1)
        composeRule.onAllNodesWithText("裂語  レツゴ").assertCountEquals(1)
        composeRule.onAllNodesWithText("Kanji not found").assertCountEquals(1)

        composeRule.onNodeWithText("Back to Browse").performClick()
        composeRule.onNodeWithText("Review now").performClick()
        assertTrue(homeClicked)
        assertTrue(reviewClicked)
    }
}
