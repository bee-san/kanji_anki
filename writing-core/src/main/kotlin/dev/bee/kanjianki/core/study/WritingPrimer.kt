package dev.bee.kanjianki.core.study

/**
 * The ungraded primer shown before a kanji's first guided writing attempt:
 * watch the strokes, write it from memory, compare with the model. Nothing in
 * the primer is submitted as a review or counted as a hint.
 */
enum class WritingPrimerStage {
    /** The model strokes animate; the pad does not accept ink. */
    WATCH,

    /** Blank pad, no guide, no off-guide blocking. */
    WRITE,

    /** The learner's strokes beside the model, numbered and colour-coded. */
    COMPARE,
    ;

    /**
     * The reference panel appears only beside the comparison. While watching,
     * the pad must stay on screen for the animation, and the prompt already
     * carries the word, reading and meaning; while writing, it would give the
     * answer away.
     */
    fun referenceVisible(): Boolean = this == COMPARE

    fun padInputEnabled(): Boolean = this != WATCH

    fun guideRevealed(): Boolean = this == COMPARE

    companion object {
        /**
         * A primer runs only on a kanji's first writing appearance: a real
         * `write_kanji` card the learner has never written, opening at the
         * fully guided level, with stroke data to animate and compare against.
         * Retries and repeats go straight to the graded attempt.
         */
        @JvmStatic
        fun shouldPrime(
            writeKanjiCard: Boolean,
            firstAppearance: Boolean,
            initialState: HintState?,
            guide: StrokeGuide?,
        ): Boolean {
            return writeKanjiCard &&
                firstAppearance &&
                guide != null &&
                !guide.isEmpty() &&
                (initialState ?: HintState.initial()).level() == HintLevel.TRACE
        }
    }
}
