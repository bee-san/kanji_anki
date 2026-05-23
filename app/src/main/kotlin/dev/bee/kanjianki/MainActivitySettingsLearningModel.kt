package dev.bee.kanjianki

object SettingsLearningStepsTestTags {
    const val NEW_STEPS_INPUT = "settings-learning-new-steps-input"
    const val REVIEW_STEPS_INPUT = "settings-learning-review-steps-input"
}

fun interface SettingsLearningStepsSaveAction {
    fun save(newStepsText: String, reviewStepsText: String)
}

data class SettingsLearningStepsPanelModel(
    val title: String,
    val body: String,
    val newCardsLabel: String,
    val initialNewStepsText: String,
    val reviewMissesLabel: String,
    val initialReviewStepsText: String,
    val defaultNewStepsText: String,
    val defaultReviewStepsText: String,
    val ankiDefaultLabel: String,
    val sameStepsLabel: String,
    val saveLabel: String,
    val onSave: SettingsLearningStepsSaveAction,
) : SettingsPanelModel
