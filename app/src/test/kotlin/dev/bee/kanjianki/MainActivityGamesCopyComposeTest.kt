package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun gamesModeCardUsesJapaneseTitleAndAccessibilityLabel() {
        withLocale(Locale.JAPAN) {
            composeRule.setContent {
                GamesScreen(
                    GamesScreenModel(
                        title = KanjiGameCopy.gamesLabel(),
                        subtitle = KanjiGameCopy.gamesSubtitle(),
                        emptyTitle = null,
                        emptyBody = null,
                        showSyncButton = false,
                        onSync = Runnable {},
                        modeCards = listOf(
                            GamesModeCardModel(
                                title = KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.MEANING_POP),
                                label = KanjiGameCopy.modeLabel(KanjiGameEngine.GameMode.MEANING_POP),
                                body = KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, available = true),
                                accentColor = 0xFFFF6B6B.toInt(),
                                available = true,
                                chipLabel = KanjiGameCopy.playLabel(),
                                onClick = Runnable {},
                            )
                        ),
                    )
                )
            }

            composeRule.onNodeWithText("意味ポップ").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("ゲームモードカード, 意味ポップ, 漢字→意味, 集中リストから意味を選びます。, 開始")
                .assertIsDisplayed()
        }
    }

    @Test
    fun gameQuestionCardUsesJapaneseReadingPromptInstructions() {
        withLocale(Locale.JAPAN) {
            val readingQuestion = KanjiGameEngine.GameQuestion(
                KanjiGameEngine.GameMode.READING_RUSH,
                "語",
                "言語",
                "Pick the reading for 語",
                "げんご",
                listOf("げんご", "ことば"),
                "語 = language",
            )

            composeRule.setContent {
                GamesQuestionCard(question = readingQuestion, onChoiceSelected = {})
            }

            composeRule.onNodeWithText("単語→読み").assertIsDisplayed()
            composeRule.onNodeWithText("語の読みを選びます。").assertIsDisplayed()
            composeRule.onAllNodesWithText("Pick the reading for 語").assertCountEquals(0)
        }
    }

    @Test
    fun gameQuestionCardUsesJapaneseConfusablePromptInstructions() {
        withLocale(Locale.JAPAN) {
            val confusableQuestion = KanjiGameEngine.GameQuestion(
                KanjiGameEngine.GameMode.CONFUSABLE_CLASH,
                "裂",
                "Which kanji means split?",
                "Watch the shape",
                "裂",
                listOf("裂", "提"),
                "裂 = split",
            )

            composeRule.setContent {
                GamesQuestionCard(question = confusableQuestion, onChoiceSelected = {})
            }

            composeRule.onNodeWithText("「split」を表す漢字は？").assertIsDisplayed()
            composeRule.onNodeWithText("形を見比べます。").assertIsDisplayed()
            composeRule.onAllNodesWithText("Which kanji means split?").assertCountEquals(0)
            composeRule.onAllNodesWithText("Watch the shape").assertCountEquals(0)
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
