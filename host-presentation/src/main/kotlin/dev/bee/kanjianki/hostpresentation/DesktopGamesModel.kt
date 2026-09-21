package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.GamesRender
import dev.bee.kanjianki.core.KanjiGameCopy
import dev.bee.kanjianki.core.KanjiGameEngine
import dev.bee.kanjianki.presentation.GamesAccent
import dev.bee.kanjianki.presentation.GamesChoice
import dev.bee.kanjianki.presentation.GamesMenu
import dev.bee.kanjianki.presentation.GamesModeCard
import dev.bee.kanjianki.presentation.GamesResult
import dev.bee.kanjianki.presentation.GamesRound
import dev.bee.kanjianki.presentation.GamesScreen
import dev.bee.kanjianki.presentation.GamesState
import dev.bee.kanjianki.presentation.KaniAction

/**
 * Maps the engine-facing [GamesRender] to the portable `GamesScreen`.
 *
 * Every string is `KanjiGameCopy`'s — the same the Android host used — so this is a
 * pure translation. Its only work is choosing the state (menu / round / result), the
 * accent per mode, and flattening the round/result into the shared shape.
 */
object DesktopGamesModel {
    fun screen(render: GamesRender): GamesScreen = when {
        render.showingResult -> GamesScreen(state = GamesState.RESULT, result = result(render))
        render.inRound -> GamesScreen(state = GamesState.ROUND, round = round(render))
        render.data == null -> GamesScreen(state = GamesState.UNAVAILABLE)
        else -> GamesScreen(state = GamesState.MENU, menu = menu(render))
    }

    private fun menu(render: GamesRender): GamesMenu {
        val available = render.availableModes.toSet()
        val modes = KanjiGameEngine.GameMode.values().map { mode ->
            val isAvailable = mode in available
            GamesModeCard(
                id = mode.id,
                title = KanjiGameCopy.modeTitle(mode),
                label = KanjiGameCopy.modeLabel(mode),
                body = KanjiGameCopy.modeBody(mode, isAvailable),
                accent = accent(mode),
                available = isAvailable,
                chipLabel = if (isAvailable) KanjiGameCopy.playLabel() else KanjiGameCopy.lockedLabel(),
            )
        }
        // Nothing playable means an empty (or too-thin) collection; the menu says so
        // and offers a sync.
        val empty = render.availableModes.isEmpty()
        return GamesMenu(
            title = KanjiGameCopy.gamesLabel(),
            subtitle = KanjiGameCopy.gamesSubtitle(),
            modes = modes,
            needsSync = empty,
            emptyTitle = if (empty) KanjiGameCopy.gamesLabel() else null,
            emptyBody = if (empty) KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, false) else null,
        )
    }

    private fun round(render: GamesRender): GamesRound {
        val question = render.question!!
        val roundState = render.round
        return GamesRound(
            roundLabel = KanjiGameCopy.roundLabel(),
            roundValue = "${roundState.progress(true)}/${roundState.totalQuestions}",
            scoreLabel = KanjiGameCopy.scoreLabel(),
            scoreValue = "${roundState.correct}/${roundState.totalQuestions}",
            streakLabel = KanjiGameCopy.streakLabel(),
            streakValue = roundState.streak.toString(),
            scoreDescription = KanjiGameCopy.scoreStripDescription(roundState.correct, roundState.totalQuestions),
            modeLabel = KanjiGameCopy.modeLabel(question.mode),
            prompt = KanjiGameCopy.questionPrompt(question),
            promptDetail = KanjiGameCopy.questionPromptDetail(question),
            choices = question.choices.map { choice ->
                GamesChoice(value = choice, label = KanjiGameCopy.choiceLabel(question, choice).orEmpty().ifBlank { choice })
            },
            accent = accent(question.mode),
            choicesAreKanji = KanjiGameCopy.choiceUsesKanjiTypography(question),
        )
    }

    private fun result(render: GamesRender): GamesResult {
        val question = render.question!!
        val roundState = render.round
        val roundComplete = roundState.roundComplete()
        val correct = render.lastCorrect
        return GamesResult(
            title = if (roundComplete) KanjiGameCopy.roundCompleteLabel() else KanjiGameCopy.resultTitle(false, correct),
            correct = correct,
            finalScore = if (roundComplete) KanjiGameCopy.finalScoreText(roundState.correct, roundState.totalQuestions) else null,
            accuracy = if (roundComplete) KanjiGameCopy.accuracyText(roundState.correct, roundState.answered) else null,
            answer = if (correct) null else KanjiGameCopy.answerText(question.correctAnswer),
            selected = if (correct) null else KanjiGameCopy.selectedAnswerText(render.lastSelected),
            explanation = if (correct) null else question.explanation.ifBlank { null },
            primaryLabel = if (roundComplete) KanjiGameCopy.newRoundLabel() else KanjiGameCopy.playLabel(),
            // Both play-again and next-question advance the runtime; the runtime
            // decides whether that is a fresh round or the next question.
            primaryAction = KaniAction.Game.Continue,
        )
    }

    private fun accent(mode: KanjiGameEngine.GameMode): GamesAccent = when (mode) {
        KanjiGameEngine.GameMode.MEANING_POP -> GamesAccent.MEANING
        KanjiGameEngine.GameMode.READING_RUSH -> GamesAccent.READING
        KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> GamesAccent.CONFUSABLE
        KanjiGameEngine.GameMode.MISS_SWEEP -> GamesAccent.MISS_SWEEP
    }
}
