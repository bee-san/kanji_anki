package dev.bee.kanjianki.presentation

/**
 * The kanji games surface, as portable data both hosts render.
 *
 * The Android host drove games from `MainActivityGames` and `KanjiGameEngine`; this is
 * the same surface as one value. The engine that generates questions, scores answers,
 * and decides when a round ends stays in `:core` — a leaf feature never reaches it. A
 * host maps its engine state to this, and the surface renders the branch.
 *
 * [state] is the whole screen's shape: the mode menu, an in-progress round, or a
 * result. They are separate because the menu has no question and the result has no
 * live choices.
 */
data class GamesScreen(
    val state: GamesState,
    val menu: GamesMenu? = null,
    val round: GamesRound? = null,
    val result: GamesResult? = null,
)

/** The games screen's top-level shape. */
enum class GamesState {
    MENU,
    ROUND,
    RESULT,
    UNAVAILABLE,
}

/**
 * The mode picker.
 *
 * [emptyTitle]/[emptyBody] are non-null when nothing can be played yet — an empty
 * collection needs a sync first — and [needsSync] gates the sync button. Each mode is
 * a [GamesModeCard]; an unavailable mode (too few recent misses for Miss Sweep) is
 * shown disabled with its reason rather than hidden.
 */
data class GamesMenu(
    val title: String,
    val subtitle: String,
    val modes: List<GamesModeCard>,
    val needsSync: Boolean = false,
    val emptyTitle: String? = null,
    val emptyBody: String? = null,
)

data class GamesModeCard(
    val id: String,
    val title: String,
    val label: String,
    val body: String,
    val accent: GamesAccent,
    val available: Boolean,
    val chipLabel: String,
) {
    init {
        require(id.isNotBlank()) { "a game mode needs an id" }
    }

    /** Starting a mode dispatches its id; an unavailable mode dispatches nothing. */
    val action: KaniAction?
        get() = if (available) KaniAction.Game.Start(modeId = id) else null
}

/** A colour role for a mode card, mapped to the active theme by the surface. */
enum class GamesAccent {
    MEANING,
    READING,
    CONFUSABLE,
    MISS_SWEEP,
}

/**
 * A live round: the score so far, the current question, and its choices.
 *
 * Picking a choice grades it — there is no separate submit — so each [GamesChoice]
 * carries its own answer action. [scoreDescription] is the merged announcement for the
 * three score tiles, so a screen reader gets one sentence.
 */
data class GamesRound(
    val roundLabel: String,
    val roundValue: String,
    val scoreLabel: String,
    val scoreValue: String,
    val streakLabel: String,
    val streakValue: String,
    val scoreDescription: String,
    val modeLabel: String,
    val prompt: String,
    val promptDetail: String,
    val choices: List<GamesChoice>,
    val accent: GamesAccent,
    /** Kanji-glyph choices render larger than word/meaning choices. */
    val choicesAreKanji: Boolean = false,
)

data class GamesChoice(
    val value: String,
    val label: String,
) {
    init {
        require(value.isNotBlank()) { "a game choice needs a value" }
    }

    /** Picking answers the round with this choice. */
    val action: KaniAction
        get() = KaniAction.Game.Answer(answer = value)
}

/**
 * The end-of-round result.
 *
 * [correct] tells the surface whether the last answer was right, for colouring.
 * [answer]/[selected]/[explanation] are shown when the round ended on a wrong answer;
 * [finalScore]/[accuracy] when it ended by running out. The primary action either
 * plays again or returns to the menu, decided by the host.
 */
data class GamesResult(
    val title: String,
    val correct: Boolean,
    val finalScore: String? = null,
    val accuracy: String? = null,
    val answer: String? = null,
    val selected: String? = null,
    val explanation: String? = null,
    val primaryLabel: String,
    val primaryAction: KaniAction,
)
