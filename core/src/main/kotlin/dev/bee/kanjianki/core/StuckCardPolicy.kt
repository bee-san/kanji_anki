package dev.bee.kanjianki.core

/**
 * Detects chronically-stuck study items (Goal 68, phase 1).
 *
 * At the demotion floor the fail streak deliberately keeps accumulating
 * (see `ReviewTransitionEngine.applyReviewAgain` and AGENTS.md) so the signal
 * survives, but nothing acted on it beyond a stats count. A "stuck" item is a
 * `REVIEW`-phase card sitting on its own per-item demotion floor whose real
 * again-streak has reached twice the demotion threshold — i.e. it has failed
 * long enough that a demotion would have fired twice over but the floor left
 * it in place. The floor is computed per item because a card without
 * similar-kanji content has a different floor than one that has it.
 */
object StuckCardPolicy {
    /** Multiple of the demotion fail streak at which a floor card is "stuck". */
    const val STUCK_FAIL_STREAK_MULTIPLIER: Int = 2

    @JvmStatic
    fun isStuck(
        state: String?,
        rung: RecordsBase.LadderRung?,
        phase: RecordsBase.SchedulerPhase?,
        realAgainStreak: Int,
        hasSimilarKanji: Boolean,
        ladder: RecordsBase.StudyLadderSettings?,
        ladderDemotionFailStreak: Int,
    ): Boolean {
        if (StudyLadderRules.STATE_RETIRED == state.orEmpty()) {
            return false
        }
        if (phase != RecordsBase.SchedulerPhase.REVIEW) {
            return false
        }
        val safeRung = rung ?: return false
        val safeLadder = StudyLadderRules.safeLadder(ladder)
        // The item is on its demotion floor when moving down cannot change the
        // rung for this specific card (respects the per-item similar-kanji
        // availability).
        if (safeLadder.previousRung(safeRung, hasSimilarKanji) != safeRung) {
            return false
        }
        val failStreak = maxOf(1, ladderDemotionFailStreak)
        return maxOf(0, realAgainStreak) >= STUCK_FAIL_STREAK_MULTIPLIER * failStreak
    }
}
