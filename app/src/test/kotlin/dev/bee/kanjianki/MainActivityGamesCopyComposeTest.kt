package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityGamesCopyComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gamesScreenUsesJapaneseCopyHelpers() {
        withLocale(Locale.JAPAN) {
            composeRule.setContent {
                GamesScreen(
                    GamesScreenModel(
                        title = KanjiGameCopy.gamesLabel(),
                        subtitle = KanjiGameCopy.gamesSubtitle(),
                        emptyTitle = KanjiGameCopy.emptyNoKanjiTitle(),
                        emptyBody = KanjiGameCopy.emptyNoKanjiBody(),
                        showSyncButton = true,
                        onSync = Runnable {},
                        modeCards = emptyList(),
                    )
                )
            }

            composeRule.onNodeWithText("ゲーム").assertIsDisplayed()
            composeRule.onNodeWithText("復習を変更せずに練習できます。").assertIsDisplayed()
            composeRule.onNodeWithText("AnkiDroidを同期").assertIsDisplayed()
        }
    }

    @Test
    fun gameQuestionCardUsesJapaneseModeLabel() {
        withLocale(Locale.JAPAN) {
            val question = KanjiGameEngine.GameQuestion(
                KanjiGameEngine.GameMode.MEANING_POP,
                "語",
                "語",
                "Pick the meaning",
                "language",
                listOf("language", "word"),
                "語 = language",
            )

            composeRule.setContent {
                GamesQuestionCard(question = question, onChoiceSelected = {})
            }

            composeRule.onNodeWithText("漢字→意味").assertIsDisplayed()
        }
    }

    @Test
    fun gameResultCardUsesJapaneseCopyHelpers() {
        withLocale(Locale.JAPAN) {
            composeRule.setContent {
                GamesResultCard(
                    GamesResultModel(
                        title = KanjiGameCopy.resultTitle(roundComplete = false, correct = true),
                        titleColor = 0xFF00AEB5.toInt(),
                        finalScore = null,
                        accuracy = null,
                        answer = KanjiGameCopy.answerText("language"),
                        selectedAnswer = null,
                        explanation = null,
                        primaryLabel = KanjiGameCopy.nextLabel(),
                        primaryColor = 0xFF00AEB5.toInt(),
                        onPrimary = Runnable {},
                        onGames = Runnable {},
                    )
                )
            }

            composeRule.onNodeWithText("正解").assertIsDisplayed()
            composeRule.onNodeWithText("正解: language").assertIsDisplayed()
            composeRule.onNodeWithText("次へ").assertIsDisplayed()
            composeRule.onNodeWithText("ゲーム").assertIsDisplayed()
        }
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val original = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
