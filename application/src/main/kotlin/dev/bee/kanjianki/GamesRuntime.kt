package dev.bee.kanjianki

import dev.bee.kanjianki.application.HomeUseCases
import dev.bee.kanjianki.core.KanjiGameEngine
import dev.bee.kanjianki.core.KanjiGameRoundState
import dev.bee.kanjianki.data.HomeGameDataSnapshot
import java.util.Random

/**
 * One games session, driven portably.
 *
 * The engine-facing half of the Games surface, shared by both hosts: it lists the
 * playable modes, generates each round's question with `KanjiGameEngine`, tracks the
 * `KanjiGameRoundState` score, and scores answers — the same pipeline
 * `MainActivityGames` ran, minus the Android UI. A host maps the [GamesRender] this
 * produces to the portable [dev.bee.kanjianki.presentation.GamesScreen].
 *
 * Host-agnostic: no dispatcher, no Compose. The one bit of nondeterminism a game needs
 * — which question comes next — is a `Random` seeded from the caller's `nowMillis`, so
 * a test can pin it and both hosts play the same way from the same seed.
 *
 * Games never touch the scheduler or the collection: a round is scored in memory and
 * discarded. That is why this reads `HomeGameDataSnapshot` (rows, inventory, pairs)
 * and writes nothing back.
 */
class GamesRuntime(private val useCases: HomeUseCases) {
    private val engine = KanjiGameEngine()
    private var data: HomeGameDataSnapshot? = null
    private var mode: KanjiGameEngine.GameMode? = null
    private var round = KanjiGameRoundState.newRound(ROUND_QUESTIONS)
    private var question: KanjiGameEngine.GameQuestion? = null
    private var lastCorrect = false
    private var lastSelected: String? = null

    /** The current render: the menu, an in-progress round, or a result. */
    fun render(): GamesRender = GamesRender(
        data = data,
        availableModes = availableModes(),
        mode = mode,
        round = round,
        question = question,
        lastCorrect = lastCorrect,
        lastSelected = lastSelected,
    )

    /** Loads the game data and returns to the mode menu. */
    suspend fun menu(): GamesRender {
        data = useCases.loadGameData()
        mode = null
        question = null
        return render()
    }

    /** Starts a mode: a fresh round and its first question. */
    fun start(modeId: String, nowMillis: Long): GamesRender {
        val selected = KanjiGameEngine.GameMode.values().firstOrNull { it.id == modeId } ?: return render()
        mode = selected
        round = KanjiGameRoundState.newRound(ROUND_QUESTIONS)
        lastSelected = null
        question = nextQuestion(selected, nowMillis)
        return render()
    }

    /**
     * Scores an answer against the current question.
     *
     * A second answer to the same graded question is ignored — the render moves to the
     * result once answered, so the choices are no longer shown; this guards the same
     * double-commit a key-repeat could cause. The round state advances only here.
     */
    fun answer(value: String, nowMillis: Long): GamesRender {
        val current = question ?: return render()
        if (lastSelected != null) return render()
        lastSelected = value
        lastCorrect = current.isCorrect(value)
        round = round.answer(lastCorrect)
        return render()
    }

    /** Advances past the result: the next question, or the menu when the round is done. */
    fun advance(nowMillis: Long): GamesRender {
        val current = mode
        if (current == null || lastSelected == null) return render()
        lastSelected = null
        if (round.roundComplete()) {
            question = null
            mode = null
            return render()
        }
        question = nextQuestion(current, nowMillis)
        return render()
    }

    private fun availableModes(): List<KanjiGameEngine.GameMode> {
        val snapshot = data ?: return emptyList()
        return engine.availableModes(snapshot.activeRows, snapshot.inventory, snapshot.similarPairs)
    }

    private fun nextQuestion(mode: KanjiGameEngine.GameMode, nowMillis: Long): KanjiGameEngine.GameQuestion? {
        val snapshot = data ?: return null
        return engine.nextQuestion(
            mode,
            snapshot.activeRows,
            snapshot.inventory,
            snapshot.similarPairs,
            Random(nowMillis),
        )
    }

    private companion object {
        const val ROUND_QUESTIONS = 10
    }
}

/**
 * What a host renders: the loaded game data and the live engine state.
 *
 * Raw engine types rather than the portable model, because the host owns the
 * mapping — the same split `StudyRouteRender` uses. [availableModes] is computed
 * against the data so the menu can show every mode with its availability.
 */
data class GamesRender(
    val data: HomeGameDataSnapshot?,
    val availableModes: List<KanjiGameEngine.GameMode>,
    val mode: KanjiGameEngine.GameMode?,
    val round: KanjiGameRoundState,
    val question: KanjiGameEngine.GameQuestion?,
    val lastCorrect: Boolean,
    val lastSelected: String?,
) {
    /** True while a round is in progress and its question is unanswered. */
    val inRound: Boolean
        get() = mode != null && question != null && lastSelected == null

    /** True once the current question has been answered, showing the result. */
    val showingResult: Boolean
        get() = mode != null && question != null && lastSelected != null
}
