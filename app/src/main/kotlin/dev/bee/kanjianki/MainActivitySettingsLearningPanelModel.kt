package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SettingsTextCopy

internal data class SettingsLearningStepsTextState(
    val newStepsText: String,
    val reviewStepsText: String,
)

internal object SettingsLearningStepsPanelModels {
    @JvmStatic
    fun create(
        current: RecordsSchedulerModels.LearningStepSettings,
        onSave: SettingsLearningStepsSaveAction,
    ): SettingsLearningStepsPanelModel {
        val defaults = RecordsSchedulerModels.LearningStepSettings.defaults()
        return SettingsLearningStepsPanelModel(
            title = SettingsTextCopy.learningStepsTitle(),
            body = SettingsTextCopy.learningStepsBody(),
            newCardsLabel = MainActivityBase.LABEL_NEW_CARDS,
            initialNewStepsText = current.newStepsText(),
            reviewMissesLabel = SettingsTextCopy.reviewMissesLabel(),
            initialReviewStepsText = current.reviewStepsText(),
            defaultNewStepsText = defaults.newStepsText(),
            defaultReviewStepsText = defaults.reviewStepsText(),
            ankiDefaultLabel = SettingsTextCopy.ankiDefaultLabel(),
            sameStepsLabel = SettingsTextCopy.sameLearningStepsLabel(),
            saveLabel = SettingsTextCopy.saveLearningStepsLabel(),
            onSave = onSave,
        )
    }

    @JvmStatic
    fun useNewCardStepsTextState(currentNewStepsText: String): SettingsLearningStepsTextState {
        return SettingsLearningStepsTextState(currentNewStepsText, currentNewStepsText)
    }
}
