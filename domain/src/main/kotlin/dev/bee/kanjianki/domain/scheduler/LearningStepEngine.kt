package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyRating

class LearningStepEngine {
    fun apply(input: LearningStepInput): LearningStepResult {
        require(input.phase == StudyPhase.NEW_LEARNING || input.phase == StudyPhase.RELEARNING) {
            "LearningStepEngine only handles learning and relearning phases"
        }
        val steps = input.settings.stepsFor(input.phase)
        if (steps.isEmpty()) {
            return LearningStepResult.Graduate
        }
        return when (input.rating) {
            StudyRating.AGAIN -> repeatAt(
                phase = input.phase,
                stepIndex = 0,
                dueAtMillis = input.nowMillis + steps[0].toMillis(),
            )
            StudyRating.HARD -> hard(input, steps)
            StudyRating.GOOD -> good(input, steps)
            StudyRating.EASY -> LearningStepResult.Graduate
        }
    }

    private fun hard(input: LearningStepInput, steps: List<Int>): LearningStepResult {
        val index = input.currentStepIndex.coerceAtLeast(0)
        val delayMillis = if (index == 0 && steps.size >= 2) {
            val first = steps[0].toMillis()
            val second = steps[1].toMillis()
            maxOf(first, (first + second) / 2L)
        } else {
            steps[index.coerceAtMost(steps.lastIndex)].toMillis()
        }
        return repeatAt(
            phase = input.phase,
            stepIndex = index,
            dueAtMillis = input.nowMillis + delayMillis,
        )
    }

    private fun good(input: LearningStepInput, steps: List<Int>): LearningStepResult {
        val nextIndex = input.currentStepIndex + 1
        if (nextIndex >= steps.size) {
            return LearningStepResult.Graduate
        }
        return repeatAt(
            phase = input.phase,
            stepIndex = nextIndex,
            dueAtMillis = input.nowMillis + steps[nextIndex].toMillis(),
        )
    }

    private fun repeatAt(
        phase: StudyPhase,
        stepIndex: Int,
        dueAtMillis: Long,
    ): LearningStepResult.Repeat = LearningStepResult.Repeat(
        phase = phase,
        stepIndex = stepIndex,
        dueAtMillis = dueAtMillis,
    )

    private fun Int.toMillis(): Long = this * 60_000L
}

data class LearningStepInput(
    val phase: StudyPhase,
    val currentStepIndex: Int,
    val rating: StudyRating,
    val nowMillis: Long,
    val settings: LearningStepSettings = LearningStepSettings.defaults,
)

data class LearningStepSettings(
    val newStepsMinutes: List<Int>,
    val relearningStepsMinutes: List<Int>,
) {
    init {
        require(newStepsMinutes.isNotEmpty()) { "newStepsMinutes must not be empty" }
        require(newStepsMinutes.all { it > 0 }) { "newStepsMinutes must contain positive values" }
        require(relearningStepsMinutes.all { it > 0 }) {
            "relearningStepsMinutes must contain positive values"
        }
    }

    fun stepsFor(phase: StudyPhase): List<Int> = when (phase) {
        StudyPhase.NEW_LEARNING -> newStepsMinutes
        StudyPhase.RELEARNING -> relearningStepsMinutes
        StudyPhase.REVIEW -> error("Review phase does not have learning steps")
    }

    companion object {
        val defaults = LearningStepSettings(
            newStepsMinutes = listOf(1, 10),
            relearningStepsMinutes = listOf(10),
        )
    }
}

sealed interface LearningStepResult {
    data class Repeat(
        val phase: StudyPhase,
        val stepIndex: Int,
        val dueAtMillis: Long,
    ) : LearningStepResult

    data object Graduate : LearningStepResult
}
