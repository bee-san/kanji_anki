package dev.bee.kanjianki

import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine
import dev.bee.kanjianki.core.KanjiGameRoundState
import dev.bee.kanjianki.core.RecordsImportModels
import java.security.SecureRandom
import java.util.Random

internal abstract class MainActivityGames : MainActivityHome() {
    private val gameEngine = KanjiGameEngine()
    private val gameRandom: Random = SecureRandom()
    private var gameRound = KanjiGameRoundState.newRound(GAME_ROUND_QUESTIONS)
    private var cachedGameData: GameData? = null

    override fun renderGames() {
        clearGameSession()
        renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.gamesActionLabel(),
            load = { gamesScreenModel() },
            render = { model ->
                renderHomeRoute {
                    GamesMenuScreen(
                        model = model,
                        onHome = this::renderHome
                    )
                }
            },
        )
    }

    internal fun returnToGames() {
        renderGames()
    }

    private fun clearGameSession() {
        gameRound = KanjiGameRoundState.newRound(GAME_ROUND_QUESTIONS)
        cachedGameData = null
    }

    fun gamesScreenModel(): GamesScreenModel {
        val data = currentGameData()
        val cards = if (data.hasKanji()) {
            val available = gameEngine.availableModes(data.rows, data.inventory, data.pairs)
            KanjiGameEngine.GameMode.values().map { mode ->
                val modeAvailable = available.contains(mode)
                GamesModeCardModel(
                    title = KanjiGameCopy.modeTitle(mode),
                    label = KanjiGameCopy.modeLabel(mode),
                    body = KanjiGameCopy.modeBody(mode, modeAvailable),
                    accentColor = colorForGameMode(mode),
                    available = modeAvailable,
                    chipLabel = if (modeAvailable) KanjiGameCopy.playLabel() else KanjiGameCopy.lockedLabel(),
                    onClick = if (modeAvailable) Runnable { startGame(mode) } else Runnable {}
                )
            }
        } else {
            emptyList()
        }
        return GamesScreenModel(
            title = KanjiGameCopy.gamesLabel(),
            subtitle = KanjiGameCopy.gamesSubtitle(),
            emptyTitle = if (data.hasKanji()) null else KanjiGameCopy.emptyNoKanjiTitle(),
            emptyBody = if (data.hasKanji()) null else KanjiGameCopy.emptyNoKanjiBody(),
            showSyncButton = !data.hasKanji(),
            onSync = Runnable { confirmSync() },
            modeCards = cards
        )
    }

    fun startGame(mode: KanjiGameEngine.GameMode) {
        gameRound = KanjiGameRoundState.newRound(GAME_ROUND_QUESTIONS)
        renderGameQuestion(mode)
    }

    private fun renderGameQuestion(mode: KanjiGameEngine.GameMode) {
        if (gameRound.roundComplete()) {
            renderGameRoundComplete(mode)
            return
        }
        val data = currentGameData()
        val question = gameEngine.nextQuestion(mode, data.rows, data.inventory, data.pairs, gameRandom)
        if (question == null) {
            renderGameUnavailable(mode)
            return
        }
        renderHomeRoute {
            GamesPlayScreen(title = KanjiGameCopy.modeTitle(mode), onGames = this::returnToGames, score = gameScoreModel(awaitingAnswer = true)) {
                GamesQuestionCard(
                    question = question,
                    onChoiceSelected = { choice -> answerGameQuestion(question, choice) }
                )
            }
        }
    }

    private fun renderGameUnavailable(mode: KanjiGameEngine.GameMode) {
        val model = GamesUnavailableModel(KanjiGameCopy.gameNotReadyTitle(), KanjiGameCopy.gameNotReadyBody())
        renderHomeRoute {
            GamesPlayScreen(title = KanjiGameCopy.modeTitle(mode), onGames = this::returnToGames) {
                GamesUnavailableCard(model)
            }
        }
    }

    private fun gameScoreModel(awaitingAnswer: Boolean): GamesScoreStripModel {
        val roundProgress = gameRound.progress(awaitingAnswer)
        return GamesScoreStripModel(
            roundLabel = KanjiGameCopy.roundLabel(),
            roundValue = "$roundProgress/${gameRound.totalQuestions}",
            scoreLabel = KanjiGameCopy.scoreLabel(),
            scoreValue = "${gameRound.correct}/${gameRound.totalQuestions}",
            streakLabel = KanjiGameCopy.streakLabel(),
            streakValue = gameRound.streak.toString()
        )
    }

    private fun answerGameQuestion(question: KanjiGameEngine.GameQuestion, selected: String) {
        val correct = question.isCorrect(selected)
        gameRound = gameRound.answer(correct)
        renderGameResult(question, selected, correct)
    }

    private fun renderGameResult(
        question: KanjiGameEngine.GameQuestion,
        selected: String,
        correct: Boolean
    ) {
        val roundComplete = gameRound.roundComplete()
        val color = gameResultTitleColor(roundComplete, correct)
        val primaryLabel = gamePrimaryLabel(roundComplete)
        val primaryAction = gamePrimaryAction(question.mode, roundComplete)
        renderHomeRoute {
            GamesPlayScreen(title = KanjiGameCopy.modeTitle(question.mode), onGames = this::returnToGames, score = gameScoreModel(awaitingAnswer = false)) {
                GamesResultCard(
                    GamesResultModel(
                        title = KanjiGameCopy.resultTitle(roundComplete, correct),
                        titleColor = color,
                        finalScore = gameFinalScore(roundComplete),
                        accuracy = gameAccuracy(roundComplete),
                        answer = KanjiGameCopy.answerText(question.correctAnswer),
                        selectedAnswer = gameSelectedAnswer(selected, correct),
                        explanation = question.explanation,
                        primaryLabel = primaryLabel,
                        primaryColor = colorForGameMode(question.mode),
                        onPrimary = primaryAction,
                        onGames = Runnable { renderGames() }
                    )
                )
            }
        }
    }

    private fun renderGameRoundComplete(mode: KanjiGameEngine.GameMode) {
        renderHomeRoute {
            GamesPlayScreen(title = KanjiGameCopy.modeTitle(mode), onGames = this::returnToGames, score = gameScoreModel(awaitingAnswer = false)) {
                GamesResultCard(
                    GamesResultModel(
                        title = KanjiGameCopy.roundCompleteLabel(),
                        titleColor = BLUE,
                        finalScore = KanjiGameCopy.finalScoreText(gameRound.correct, gameRound.totalQuestions),
                        accuracy = KanjiGameCopy.accuracyText(gameRound.correct, gameRound.answered),
                        answer = null,
                        selectedAnswer = null,
                        explanation = null,
                        primaryLabel = KanjiGameCopy.newRoundLabel(),
                        primaryColor = colorForGameMode(mode),
                        onPrimary = Runnable { startGame(mode) },
                        onGames = Runnable { renderGames() }
                    )
                )
            }
        }
    }

    private fun gameResultTitleColor(roundComplete: Boolean, correct: Boolean): Int {
        if (roundComplete) {
            return BLUE
        }
        return if (correct) TEAL else CORAL
    }

    private fun colorForGameMode(mode: KanjiGameEngine.GameMode): Int {
        return when (mode) {
            KanjiGameEngine.GameMode.MEANING_POP -> CORAL
            KanjiGameEngine.GameMode.READING_RUSH -> TEAL
            KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> BLUE
        }
    }

    private fun gameFinalScore(roundComplete: Boolean): String? {
        return if (roundComplete) {
            KanjiGameCopy.finalScoreText(gameRound.correct, gameRound.totalQuestions)
        } else {
            null
        }
    }

    private fun gameAccuracy(roundComplete: Boolean): String? {
        return if (roundComplete) {
            KanjiGameCopy.accuracyText(gameRound.correct, gameRound.answered)
        } else {
            null
        }
    }

    private fun gameSelectedAnswer(selected: String, correct: Boolean): String? {
        return if (correct) null else KanjiGameCopy.selectedAnswerText(selected)
    }

    private fun currentGameData(): GameData {
        return cachedGameData ?: gameData().also { cachedGameData = it }
    }

    private fun gamePrimaryLabel(roundComplete: Boolean): String {
        return if (roundComplete) KanjiGameCopy.newRoundLabel() else KanjiGameCopy.nextLabel()
    }

    private fun gamePrimaryAction(
        mode: KanjiGameEngine.GameMode,
        roundComplete: Boolean
    ): Runnable {
        return if (roundComplete) {
            Runnable { startGame(mode) }
        } else {
            Runnable { renderGameQuestion(mode) }
        }
    }

    protected open fun gameData(): GameData {
        return GameData(
            rows = store.activeDashboardRows().orEmpty(),
            inventory = store.searchKanjiInventory("").orEmpty(),
            pairs = store.allLocalSimilarPairs().orEmpty()
        )
    }

    internal data class GameData(
        val rows: List<RecordsImportModels.DashboardRow>,
        val inventory: List<RecordsImportModels.KanjiInventoryItem>,
        val pairs: List<RecordsImportModels.SimilarKanjiPair>
    ) {
        fun hasKanji(): Boolean = rows.isNotEmpty() || inventory.isNotEmpty()
    }

    private companion object {
        const val GAME_ROUND_QUESTIONS = 10
    }
}
