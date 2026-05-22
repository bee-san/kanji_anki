package dev.bee.kanjianki.core

object LearningStepsSettingsPolicy {
    const val STEP_FORMAT_ERROR: String = "Use steps like 1m, 10m, or 1h."

    @JvmStatic
    fun saveRequest(newStepsText: String?, reviewStepsText: String?): SaveResult {
        val parsedNew = RecordsSchedulerModels.LearningStepSettings.tryParseSteps(newStepsText)
        val parsedReview = RecordsSchedulerModels.LearningStepSettings.tryParseSteps(reviewStepsText)
        if (parsedNew.isEmpty() || parsedReview.isEmpty()) {
            return SaveResult.invalid(STEP_FORMAT_ERROR)
        }
        return SaveResult.valid(RecordsSchedulerModels.LearningStepSettings(parsedNew, parsedReview))
    }

    class SaveResult private constructor(
        @JvmField val valid: Boolean,
        @JvmField val settings: RecordsSchedulerModels.LearningStepSettings?,
        @JvmField val message: String,
    ) {
        companion object {
            @JvmStatic
            fun valid(settings: RecordsSchedulerModels.LearningStepSettings): SaveResult {
                return SaveResult(true, settings, "")
            }

            @JvmStatic
            fun invalid(message: String): SaveResult {
                return SaveResult(false, null, message)
            }
        }
    }
}
