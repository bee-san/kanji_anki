package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsRating
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Goal 60: pins the deliberate relearning-graduation difficulty rule.
 *
 * On a lapse, `applyReviewAgain` runs `review(AGAIN, …)`, updating stability and
 * difficulty. When the card later graduates from relearning,
 * `graduateToReview → initialReview(isNewLearning = false)` keeps the post-lapse
 * stability but applies `nextDifficulty(graduationRating)` a second time. This
 * is an intentional deviation from strict "no memory change on relearning-step
 * answers" parity (documented in AGENTS.md next to the graduation note). This
 * test locks the double-update value so the behavior cannot drift silently; a
 * deliberate parity change must update this test and regenerate goldens.
 */
class RelearningGraduationDifficultyTest {
    @Test
    fun relearningGraduationAppliesNextDifficultyOnceOnTopOfPostLapseState() {
        val engine = FsrsEngine.latestDefault()
        val adapter = LatestFsrsAdapter(engine)

        // Post-lapse memory state carried into relearning graduation.
        val postLapseStability = 0.96
        val postLapseDifficulty = 6.0

        val graduated = adapter.initialReview(
            StudyRatings.GOOD,
            postLapseStability,
            postLapseDifficulty,
            0.9,
            /* isNewLearning = */ false,
        )

        // Stability is unchanged (post-lapse value is kept)...
        assertEquals(postLapseStability, graduated.stability, 0.000001)
        // ...and difficulty is exactly the graduating rating's nextDifficulty
        // applied once to the post-lapse difficulty (the deliberate second step).
        val expectedDifficulty = engine.nextDifficulty(postLapseDifficulty, FsrsRating.GOOD)
        assertEquals(expectedDifficulty, graduated.difficulty, 0.000001)
    }

    @Test
    fun newLearningGraduationUsesInitialStateAloneNotTheCarriedDifficulty() {
        val engine = FsrsEngine.latestDefault()
        val adapter = LatestFsrsAdapter(engine)

        // For new-learning graduation the carried stability/difficulty are ignored;
        // the state comes from initialState(graduationRating) alone.
        val graduated = adapter.initialReview(StudyRatings.GOOD, 999.0, 1.0, 0.9, /* isNewLearning = */ true)
        val fresh = engine.initialState(FsrsRating.GOOD)
        assertEquals(fresh.stability, graduated.stability, 0.000001)
        assertEquals(fresh.difficulty, graduated.difficulty, 0.000001)
    }
}
