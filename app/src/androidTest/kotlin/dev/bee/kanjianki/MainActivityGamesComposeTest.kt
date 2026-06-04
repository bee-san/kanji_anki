package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
                            chipLabel = "Start",
                            onClick = Runnable { clicked = true }
                        ),
                        GamesModeCardModel(
                            title = "Reading Rush",
                            label = "Word -> reading",
                            body = "Needs more local kanji data.",
                            accentColor = 0xFF00AEB5.toInt(),
                            available = false,
                            chipLabel = "Needs data",
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
        composeRule.onNodeWithText("Start").assertIsDisplayed()
        composeRule.onNodeWithText("Reading Rush").assertIsDisplayed()
        composeRule.onNodeWithText("Needs data").assertIsDisplayed()

        composeRule.onNodeWithTag(gamesModeCardTestTag("Meaning Pop")).performClick()

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
        composeRule.onNodeWithTag(gamesSyncButtonTestTag())
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(syncClicked)
        }
    }

    @Test
    fun rendersGamesMenuWithHomeAction() {
        var homeClicked = false

        composeRule.setContent {
            GamesMenuScreen(
                model = GamesScreenModel(
                    title = "Games",
                    subtitle = "Practice kanji without changing SRS.",
                    emptyTitle = "No kanji games yet",
                    emptyBody = "Sync AnkiDroid first so Kani can build practice games from your own cards.",
                    showSyncButton = false,
                    onSync = Runnable {},
                    modeCards = emptyList()
                ),
                onHome = { homeClicked = true }
            )
        }

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Games").assertIsDisplayed()

        composeRule.runOnIdle {
            assertTrue(homeClicked)
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
        composeRule.onNodeWithTag(gamesChoiceButtonTestTag(KanjiGameCopy.choiceLabel(question, "language").orEmpty()))
            .performClick()

        composeRule.runOnIdle {
            assertTrue(clickedChoice == "language")
        }
    }

    @Test
    fun rendersPlayScreenWithHeaderScoreAndQuestion() {
        var gamesClicked = false
        var clickedChoice = ""
        val question = KanjiGameEngine.GameQuestion(
            KanjiGameEngine.GameMode.READING_RUSH,
            "語る",
            "語",
            "Pick the reading",
            "language word",
            listOf("かたる", "ゴ"),
            "語る = かたる"
        )

        composeRule.setContent {
            GamesPlayScreen(
                title = "Reading Rush",
                onGames = { gamesClicked = true },
                score = GamesScoreStripModel(
                    roundLabel = "Round",
                    roundValue = "2/10",
                    scoreLabel = "Score",
                    scoreValue = "1/10",
                    streakLabel = "Streak",
                    streakValue = "1"
                )
            ) {
                GamesQuestionCard(
                    question = question,
                    onChoiceSelected = { clickedChoice = it }
                )
            }
        }

        composeRule.onNodeWithText("Reading Rush").assertIsDisplayed()
        composeRule.onNodeWithTag(homeSectionActionButtonTestTag(KanjiGameCopy.LABEL_GAMES))
            .performClick()
        composeRule.onNodeWithText("2/10").assertIsDisplayed()
        composeRule.onNodeWithText("語る").assertIsDisplayed()
        composeRule.onNodeWithTag(gamesChoiceButtonTestTag(KanjiGameCopy.choiceLabel(question, "かたる").orEmpty()))
            .performClick()

        composeRule.runOnIdle {
            assertTrue(gamesClicked)
            assertTrue(clickedChoice == "かたる")
        }
    }

    @Test
    fun rendersGameResultActions() {
        var nextClicked = false
        var gamesClicked = false

        composeRule.setContent {
            GamesResultCard(
                model = GamesResultModel(
                    title = "Not quite",
                    titleColor = 0xFFFF4C76.toInt(),
                    finalScore = null,
                    accuracy = null,
                    answer = "Correct answer: language",
                    selectedAnswer = "Your answer: word",
                    explanation = "語 = language",
                    primaryLabel = "Next",
                    primaryColor = 0xFFFF4C76.toInt(),
                    onPrimary = Runnable { nextClicked = true },
                    onGames = Runnable { gamesClicked = true }
                )
            )
        }

        composeRule.onNodeWithText("Not quite").assertIsDisplayed()
        composeRule.onNodeWithText("Correct answer: language").assertIsDisplayed()
        composeRule.onNodeWithText("Your answer: word").assertIsDisplayed()
        composeRule.onNodeWithText("語 = language").assertIsDisplayed()

        composeRule.onNodeWithTag(gamesResultPrimaryButtonTestTag("Next")).performClick()
        composeRule.onNodeWithTag(gamesResultGamesButtonTestTag()).performClick()

        composeRule.runOnIdle {
            assertTrue(nextClicked)
            assertTrue(gamesClicked)
        }
    }

    @Test
    fun rendersRoundCompleteSummaryAndUnavailableCard() {
        var newRoundClicked = false

        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                GamesResultCard(
                    model = GamesResultModel(
                        title = "Round complete",
                        titleColor = 0xFF6E5CE6.toInt(),
                        finalScore = "Score: 7/10",
                        accuracy = "Accuracy: 70%",
                        answer = null,
                        selectedAnswer = null,
                        explanation = null,
                        primaryLabel = "New round",
                        primaryColor = 0xFF6E5CE6.toInt(),
                        onPrimary = Runnable { newRoundClicked = true },
                        onGames = Runnable {}
                    )
                )
                GamesUnavailableCard(
                    model = GamesUnavailableModel(
                        title = "Game not ready",
                        body = "This game needs at least two usable choices from your local kanji data."
                    )
                )
            }
        }

        composeRule.onNodeWithText("Round complete").assertIsDisplayed()
        composeRule.onNodeWithText("Score: 7/10").assertIsDisplayed()
        composeRule.onNodeWithText("Accuracy: 70%").assertIsDisplayed()
        composeRule.onNodeWithText("Game not ready").assertIsDisplayed()
        composeRule.onNodeWithText("This game needs at least two usable choices from your local kanji data.").assertIsDisplayed()

        composeRule.onNodeWithText("New round").performClick()

        composeRule.runOnIdle {
            assertTrue(newRoundClicked)
        }
    }
}
