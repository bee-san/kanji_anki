package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityGamesComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersGameModesAndWiresAvailableCardClicks() {
        var clicked = false
        composeRule.setContent {
            GamesScreen(
                model = GamesScreenModel(
                    title = "Games",
                    subtitle = "Practice kanji without changing SRS.",
                    emptyTitle = "No kanji games yet",
                    emptyBody = "Sync AnkiDroid first so Kani can build practice games from your own cards.",
                    showSyncButton = false,
                    onSync = Runnable {},
                    modeCards = listOf(
                        GamesModeCardModel(
                            title = "Meaning Pop",
                            label = "Kanji -> meaning",
                            body = "Pick meanings for kanji from your focus list.",
                            accentColor = 0xFFFF4C76.toInt(),
                            available = true,
                            chipLabel = "play",
                            onClick = Runnable { clicked = true }
                        ),
                        GamesModeCardModel(
                            title = "Reading Rush",
                            label = "Word -> reading",
                            body = "Needs more local kanji data.",
                            accentColor = 0xFF00AEB5.toInt(),
                            available = false,
                            chipLabel = "locked",
                            onClick = Runnable {}
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Games").assertIsDisplayed()
        composeRule.onNodeWithText("Practice kanji without changing SRS.").assertIsDisplayed()
        composeRule.onNodeWithText("Meaning Pop").assertIsDisplayed()
        composeRule.onNodeWithText("Kanji -> meaning").assertIsDisplayed()
        composeRule.onNodeWithText("Pick meanings for kanji from your focus list.").assertIsDisplayed()
        composeRule.onNodeWithText("play").assertIsDisplayed()
        composeRule.onNodeWithText("Reading Rush").assertIsDisplayed()
        composeRule.onNodeWithText("locked").assertIsDisplayed()

        composeRule.onNodeWithText("Meaning Pop").performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun rendersEmptyStateAndSyncButtonWhenNoKanjiAreAvailable() {
        var syncClicked = false
        composeRule.setContent {
            GamesScreen(
                model = GamesScreenModel(
                    title = "Games",
                    subtitle = "Practice kanji without changing SRS.",
                    emptyTitle = "No kanji games yet",
                    emptyBody = "Sync AnkiDroid first so Kani can build practice games from your own cards.",
                    showSyncButton = true,
                    onSync = Runnable { syncClicked = true },
                    modeCards = emptyList()
                )
            )
        }

        composeRule.onNodeWithText("No kanji games yet").assertIsDisplayed()
        composeRule.onNodeWithText("Sync AnkiDroid first so Kani can build practice games from your own cards.").assertIsDisplayed()
        composeRule.onNodeWithText("Sync AnkiDroid").assertIsDisplayed()

        composeRule.onNodeWithText("Sync AnkiDroid").performClick()

        composeRule.runOnIdle {
            assertTrue(syncClicked)
        }
    }

    @Test
    fun rendersScoreStripAndGameQuestionChoices() {
        var clickedChoice = ""
        val question = KanjiGameEngine.GameQuestion(
            KanjiGameEngine.GameMode.MEANING_POP,
            "語",
            "語",
            "Pick the meaning",
            "language",
            listOf("language", "word"),
            "語 = language"
        )

        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                GamesScoreStrip(
                    model = GamesScoreStripModel(
                        roundLabel = "Round",
                        roundValue = "1/10",
                        scoreLabel = "Score",
                        scoreValue = "0/10",
                        streakLabel = "Streak",
                        streakValue = "3"
                    )
                )
                GamesQuestionCard(
                    question = question,
                    onChoiceSelected = { clickedChoice = it }
                )
            }
        }

        composeRule.onNodeWithText("Round").assertIsDisplayed()
        composeRule.onNodeWithText("1/10").assertIsDisplayed()
        composeRule.onNodeWithText("Score").assertIsDisplayed()
        composeRule.onNodeWithText("Streak").assertIsDisplayed()
        composeRule.onNodeWithText("Pick the meaning").assertIsDisplayed()
        composeRule.onNodeWithText("語").assertIsDisplayed()
        composeRule.onNodeWithText("language").assertIsDisplayed()
        composeRule.onNodeWithText(KanjiGameCopy.choiceLabel(question, "language")).performClick()

        composeRule.runOnIdle {
            assertTrue(clickedChoice == "language")
        }
    }
}
