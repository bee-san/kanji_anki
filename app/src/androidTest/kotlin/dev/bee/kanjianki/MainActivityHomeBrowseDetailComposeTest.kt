package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityHomeBrowseDetailComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTimelineCardsAndEmptyState() {
        composeRule.setContent {
            RecoveryTimelinePanels(
                model = MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel(
                    "Recovery timeline",
                    "Recovered after today's sync",
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

        composeRule.onNodeWithText("Recovery timeline").assertIsDisplayed()
        composeRule.onNodeWithText("Recovered after today’s sync").assertIsDisplayed()
        composeRule.onNodeWithText("Needs 2 mature cards to fully support this kanji.").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Synced from AnkiDroid").assertIsDisplayed()
        composeRule.onNodeWithText("The kanji is still active.").assertIsDisplayed()
        composeRule.onNodeWithText("Source: active dashboard row").assertIsDisplayed()
    }

    @Test
    fun rendersBrowseRowsAndInvokesCallbacks() {
        var searched = ""
        var homeClicked = false
        var clickedKanji = ""

        composeRule.setContent {
            BrowseScreen(
                model = BrowseScreenModel(
                    initialQuery = " 裂 ",
                    resultHeading = "2 kanji",
                    rows = listOf(
                        BrowseKanjiRowModel(
                            kanji = "裂",
                            meaning = "split",
                            readings = "レツ",
                            summary = "2 local sources · 1 example",
                            suspended = true,
                            onClick = { clickedKanji = "裂" }
                        ),
                        BrowseKanjiRowModel(
                            kanji = "謎",
                            meaning = "Meaning not stored yet",
                            readings = "",
                            summary = "0 local sources · 1 example",
                            suspended = false,
                            onClick = { clickedKanji = "謎" }
                        )
                    ),
                    onHome = { homeClicked = true },
                    onSearch = { searched = it }
                )
            )
        }

        composeRule.onNodeWithText("Browse Kanji").assertIsDisplayed()
        composeRule.onNodeWithText("2 kanji").assertIsDisplayed()
        composeRule.onNodeWithText("split").assertIsDisplayed()
        composeRule.onNodeWithText("レツ").assertIsDisplayed()
        composeRule.onNodeWithText("SUSPENDED").assertIsDisplayed()
        composeRule.onNodeWithText("Meaning not stored yet").assertIsDisplayed()

        composeRule.onNodeWithText("Search").performClick()
        assertEquals(" 裂 ", searched)

        composeRule.onNodeWithText("split").performClick()
        assertEquals("裂", clickedKanji)

        composeRule.onNodeWithText("Home").performClick()
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

        composeRule.onNodeWithText("No matches").assertIsDisplayed()
        composeRule.onNodeWithText("No local kanji found").assertIsDisplayed()
        composeRule.onNodeWithText("Sync AnkiDroid first, or try a different search.").assertIsDisplayed()
        composeRule.onAllNodesWithText("SUSPENDED").assertCountEquals(0)
    }

    @Test
    fun rendersDetailInfoPanelsAndExampleCards() {
        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                BrowseDetailInfoPanel(
                    model = BrowseDetailPanelModel(
                        title = "",
                        lines = listOf("Current local practice evidence from AnkiDroid.", "Anki browser: deck:Japanese"),
                        color = 0xFF6E5CE6.toInt(),
                        style = BrowseDetailPanelStyle.BAND
                    )
                )
                BrowseDetailInfoPanel(
                    model = BrowseDetailPanelModel(
                        title = "Local inventory",
                        lines = listOf("1 source note/card · 2 stored examples", "Search: kanji:語"),
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

        composeRule.onNodeWithText("Current local practice evidence from AnkiDroid.").assertIsDisplayed()
        composeRule.onNodeWithText("Anki browser: deck:Japanese").assertIsDisplayed()
        composeRule.onNodeWithText("Local inventory").assertIsDisplayed()
        composeRule.onNodeWithText("1 source note/card · 2 stored examples").assertIsDisplayed()
        composeRule.onNodeWithText("Search: kanji:語").assertIsDisplayed()
        composeRule.onNodeWithText("ACTIVE").assertIsDisplayed()
        composeRule.onNodeWithText("活動語  カツドウゴ").assertIsDisplayed()
        composeRule.onNodeWithText("活動語を見た。").assertIsDisplayed()
        composeRule.onNodeWithText("active word").assertIsDisplayed()
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
                        navigationLabel = "Back to Browse Kanji",
                        onNavigate = Runnable { backClicked = true }
                    )
                )
                BrowseDetailIdentity(
                    model = BrowseDetailIdentityModel(
                        title = "split",
                        reading = "レツ",
                        suspended = true
                    )
                )
                BrowseDetailActions(
                    model = BrowseDetailActionsModel(
                        reviewLabel = "Review now",
                        onReview = Runnable { reviewClicked = true },
                        copyLabel = "Copy Anki search",
                        copiedLabel = "Copied Anki search",
                        onCopy = Runnable { copyClicked = true },
                        suspendLabel = "Unsuspend locally",
                        onSuspend = Runnable { suspendClicked = true }
                    )
                )
            }
        }

        composeRule.onNodeWithText("裂").assertIsDisplayed()
        composeRule.onNodeWithText("split").assertIsDisplayed()
        composeRule.onNodeWithText("レツ").assertIsDisplayed()
        composeRule.onNodeWithText("SUSPENDED").assertIsDisplayed()

        composeRule.onNodeWithText("Back to Browse Kanji").performClick()
        composeRule.onNodeWithText("Review now").performClick()
        composeRule.onNodeWithText("Copy Anki search").performClick()
        composeRule.onNodeWithText("Copied Anki search").assertIsDisplayed()
        composeRule.onNodeWithText("Unsuspend locally").performClick()

        assertTrue(backClicked)
        assertTrue(reviewClicked)
        assertTrue(copyClicked)
        assertTrue(suspendClicked)
    }

    @Test
    fun rendersFullDetailScreenAndMissingState() {
        var homeClicked = false
        var reviewClicked = false

        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                BrowseDetailScreen(
                    model = BrowseDetailScreenModel(
                        hero = BrowseDetailHeroModel(
                            kanji = "裂",
                            navigationLabel = "Back to Browse Kanji",
                            onNavigate = Runnable { homeClicked = true }
                        ),
                        identity = BrowseDetailIdentityModel(
                            title = "split",
                            reading = "レツ",
                            suspended = false
                        ),
                        reason = BrowseDetailPanelModel(
                            title = "",
                            lines = listOf("Current local practice evidence from AnkiDroid."),
                            color = 0xFF6E5CE6.toInt(),
                            style = BrowseDetailPanelStyle.BAND
                        ),
                        localInventory = BrowseDetailPanelModel(
                            title = "Local inventory",
                            lines = listOf("1 source note/card · 1 stored example"),
                            color = 0xFFC9F5F7.toInt(),
                            style = BrowseDetailPanelStyle.CARD
                        ),
                        actions = BrowseDetailActionsModel(
                            reviewLabel = "Review now",
                            onReview = Runnable { reviewClicked = true },
                            copyLabel = null,
                            copiedLabel = "Copied Anki search",
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

        composeRule.onNodeWithText("裂").assertIsDisplayed()
        composeRule.onNodeWithText("Local inventory").assertIsDisplayed()
        composeRule.onNodeWithText("Recovery timeline").assertIsDisplayed()
        composeRule.onNodeWithText("Examples").assertIsDisplayed()
        composeRule.onNodeWithText("裂語  レツゴ").assertIsDisplayed()
        composeRule.onNodeWithText("Kanji not found").assertIsDisplayed()

        composeRule.onNodeWithText("Back to Browse Kanji").performClick()
        composeRule.onNodeWithText("Review now").performClick()
        assertTrue(homeClicked)
        assertTrue(reviewClicked)
    }
}
