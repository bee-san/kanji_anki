package dev.bee.kanjianki.core

import java.util.Locale

object LearningStepsSettingsPolicy {
    const val STEP_FORMAT_ERROR: String = "Use steps like 1m, 10m, 1h, or 1d."

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun saveRequest(newStepsText: String?, reviewStepsText: String?): SaveResult {
        val parsedNew = RecordsSchedulerModels.LearningStepSettings.tryParseSteps(newStepsText)
        if (parsedNew.isEmpty()) {
            return SaveResult.invalid(stepFormatError())
        }
        val parsedReview = if (reviewStepsText != null && reviewStepsText.trim().isEmpty()) {
            emptyList()
        } else {
            RecordsSchedulerModels.LearningStepSettings.tryParseSteps(reviewStepsText)
        }
        if (parsedReview.isEmpty() && (reviewStepsText == null || reviewStepsText.trim().isNotEmpty())) {
            return SaveResult.invalid(stepFormatError())
        }
        return SaveResult.valid(RecordsSchedulerModels.LearningStepSettings(parsedNew, parsedReview))
    }

    @JvmStatic
    fun stepFormatError(): String = localizedText(
        STEP_FORMAT_ERROR,
        "1m、10m、1h、1d のようにステップを入力してください。",
    )

    private fun localizedText(english: String, japanese: String): String {
        return if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
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
