package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LearningStepEngineTest {
    private val engine = LearningStepEngine()

    @Test
    fun againReturnsToFirstStep() {
        val result = engine.apply(
            input(
                currentStepIndex = 1,
                rating = StudyRating.AGAIN,
            ),
        )

        assertEquals(
            LearningStepResult.Repeat(
                phase = StudyPhase.NEW_LEARNING,
                stepIndex = 0,
                dueAtMillis = NOW + 60_000L,
            ),
            result,
        )
    }

    @Test
    fun goodAdvancesThenGraduatesAfterFinalStep() {
        assertEquals(
            LearningStepResult.Repeat(
                phase = StudyPhase.NEW_LEARNING,
                stepIndex = 1,
                dueAtMillis = NOW + 10 * 60_000L,
            ),
            engine.apply(input(currentStepIndex = 0, rating = StudyRating.GOOD)),
        )

        assertEquals(
            LearningStepResult.Graduate,
            engine.apply(input(currentStepIndex = 1, rating = StudyRating.GOOD)),
        )
    }

    @Test
    fun hardOnFirstStepUsesDelayBetweenAgainAndGood() {
        val result = engine.apply(input(currentStepIndex = 0, rating = StudyRating.HARD))

        assertEquals(
            LearningStepResult.Repeat(
                phase = StudyPhase.NEW_LEARNING,
                stepIndex = 0,
                dueAtMillis = NOW + 330_000L,
            ),
            result,
        )
    }

    @Test
    fun hardOnLaterStepRepeatsCurrentStep() {
        val result = engine.apply(input(currentStepIndex = 1, rating = StudyRating.HARD))

        assertEquals(
            LearningStepResult.Repeat(
                phase = StudyPhase.NEW_LEARNING,
                stepIndex = 1,
                dueAtMillis = NOW + 10 * 60_000L,
            ),
            result,
        )
    }

    @Test
    fun easyGraduatesImmediately() {
        assertEquals(
            LearningStepResult.Graduate,
            engine.apply(input(currentStepIndex = 0, rating = StudyRating.EASY)),
        )
    }

    @Test
    fun relearningUsesReviewStepsAndPreservesPhase() {
        assertEquals(
            LearningStepResult.Graduate,
            engine.apply(
                input(
                    phase = StudyPhase.RELEARNING,
                    currentStepIndex = 0,
                    rating = StudyRating.GOOD,
                ),
            ),
        )

        assertEquals(
            LearningStepResult.Repeat(
                phase = StudyPhase.RELEARNING,
                stepIndex = 0,
                dueAtMillis = NOW + 10 * 60_000L,
            ),
            engine.apply(
                input(
                    phase = StudyPhase.RELEARNING,
                    currentStepIndex = 0,
                    rating = StudyRating.HARD,
                ),
            ),
        )
    }

    @Test
    fun emptyRelearningStepsGraduateSafely() {
        val settings = LearningStepSettings(
            newStepsMinutes = listOf(1, 10),
            relearningStepsMinutes = emptyList(),
        )

        assertEquals(
            LearningStepResult.Graduate,
            engine.apply(
                input(
                    phase = StudyPhase.RELEARNING,
                    currentStepIndex = 0,
                    rating = StudyRating.AGAIN,
                    settings = settings,
                ),
            ),
        )
    }

    @Test
    fun reviewPhaseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.apply(
                input(
                    phase = StudyPhase.REVIEW,
                    currentStepIndex = 0,
                    rating = StudyRating.GOOD,
                ),
            )
        }
    }

    private fun input(
        phase: StudyPhase = StudyPhase.NEW_LEARNING,
        currentStepIndex: Int,
        rating: StudyRating,
        settings: LearningStepSettings = LearningStepSettings.defaults,
    ): LearningStepInput = LearningStepInput(
        phase = phase,
        currentStepIndex = currentStepIndex,
        rating = rating,
        nowMillis = NOW,
        settings = settings,
    )

    private companion object {
        const val NOW = 1_000L
    }
}
