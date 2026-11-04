package dev.bee.kanjianki.presentation

/**
 * The card currently on screen, one variant per study task.
 *
 * Android rendered these from a dozen `*SessionModel` types chosen by branching on
 * `session.taskType`; this is the same set as one sealed hierarchy, so a host maps
 * the task once and the surface renders the branch. Every variant carries a [prompt]
 * (what the card asks) and the grade actions it offers — the actions are on the card
 * rather than passed alongside it because which grades a card offers is part of what
 * the card *is*: a self-graded flashcard offers Pass/Fail, a typed card grades itself
 * from the input, and the writing rung offers only Pass/Fail plus the "Save hard"
 * exception.
 *
 * The prompt and every answer field are [UiText] the host's resolver turns into
 * words, and the text itself is computed by the shared `:core` copy — the same
 * discipline Home and detail follow, so the two hosts cannot word a card differently.
 */
sealed interface StudyCard {
    /** What the card asks, shown before any answer. */
    val prompt: UiText

    /** The kanji or word the card is about, for the answer reveal and details. */
    val subject: String

    /**
     * A self-graded card: a front, a revealed back, and Pass/Fail.
     *
     * Recognition, reading, font-recognition, and sentence-reading all take this
     * shape — a prompt to recall, an answer to reveal, and the user's own judgement.
     * [answer] is shown after [StudyGradeAction.reveal] or once graded.
     */
    data class Flashcard(
        override val prompt: UiText,
        override val subject: String,
        val answer: UiText,
        val pass: StudyGradeAction,
        val fail: StudyGradeAction,
        val details: StudyAnswerDetails? = null,
        /** A small-font mined sentence front, for the sentence-reading ceiling rung. */
        val emphasizeSubjectInPrompt: Boolean = false,
    ) : StudyCard

    /**
     * A typed-answer card: an input box graded against the expected answer.
     *
     * Typing meaning and typing reading both take this shape. The card does not know
     * whether an input matches — that is the host's grading call — so it carries a
     * single [submit] action the host resolves to good/again from the typed text.
     */
    data class Typed(
        override val prompt: UiText,
        override val subject: String,
        val answer: UiText,
        val submit: StudyGradeAction,
        val inputLabel: UiText = UiText.EMPTY,
        val details: StudyAnswerDetails? = null,
    ) : StudyCard

    /**
     * A multiple-choice card: a prompt and a grid of choices.
     *
     * Meaning-kanji, similar-kanji, reading-kanji all take this shape. Each choice is
     * one [StudyChoice] carrying its own grade action, so picking is grading — there
     * is no separate submit. [correct] is the choice that would have been right, shown
     * as green feedback after a wrong pick; null before an answer.
     */
    data class Choice(
        override val prompt: UiText,
        override val subject: String,
        val choices: List<StudyChoice>,
        val correct: String? = null,
        val details: StudyAnswerDetails? = null,
    ) : StudyCard {
        init {
            require(choices.isNotEmpty()) { "a choice card needs choices" }
        }
    }

    /**
     * The handwriting rung: a prompt to write, and Pass/Fail plus the ink exception.
     *
     * The ink surface itself is Goal 196's; this carries the grades the writing rung
     * offers. Only Pass and Fail are user-selectable — Hard and Easy are never shown
     * for `write_kanji` — with one documented exception: when the evaluator judges an
     * attempt `CLOSE`, [saveHard] is offered instead of [pass], labelled "Save hard"
     * and submitting `hard`, which still counts as a pass. [saveHard] is null unless
     * that exception applies.
     */
    data class Writing(
        override val prompt: UiText,
        override val subject: String,
        val pass: StudyGradeAction,
        val fail: StudyGradeAction,
        val saveHard: StudyGradeAction? = null,
    ) : StudyCard
}

/** One choice on a [StudyCard.Choice], graded by being picked. */
data class StudyChoice(
    val value: String,
    val label: UiText = UiText.EMPTY,
    val grade: StudyGradeAction,
) {
    init {
        require(value.isNotBlank()) { "a choice needs a value" }
    }
}

/**
 * Extra material shown once a card is answered.
 *
 * The Android answer panel: the reading, meaning, breakdown, and example lines. Kept
 * as flat [UiText] lines rather than the Android model's nested sections, because the
 * surface renders them as a stack and the sectioning was a layout detail the shared
 * card does not need. Absent (null on the card) before there is anything to show.
 */
data class StudyAnswerDetails(
    val heading: UiText = UiText.EMPTY,
    val lines: List<UiText> = emptyList(),
)

/**
 * One grade the user can submit, carrying the wire rating and its label.
 *
 * [rating] is a scheduler wire name (`good`/`again`/`hard`/`easy`) the reducer must
 * not interpret — deciding what a rating means is `:application`'s job, and a reducer
 * that branched on it would be a second scheduler. The action dispatched is
 * [KaniAction.Study.Grade] carrying that string; the host's study use-case turns it
 * into a review.
 *
 * Pass maps to `good` and Fail to `again` at the host boundary, which is why the
 * label and the rating are carried together: the surface shows [label] and dispatches
 * [rating], and the mapping between them lives in whoever built the action, not in
 * the surface.
 */
data class StudyGradeAction(
    val label: UiText,
    val rating: String,
) {
    init {
        require(rating.isNotBlank()) { "a grade needs a rating" }
    }

    /** The action to dispatch when this grade is chosen. */
    val action: KaniAction
        get() = KaniAction.Study.Grade(rating = rating)
}
