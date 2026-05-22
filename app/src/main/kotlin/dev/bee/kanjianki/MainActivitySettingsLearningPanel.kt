package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.LearningStepsSettingsPolicy
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsLearningPanel(private val activity: MainActivitySettings) {
    fun learningStepsSettingsPanelModel(): SettingsLearningStepsPanelModel {
        val current = activity.store.learningStepSettings()
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
            onSave = SettingsLearningStepsSaveAction { newStepsText, reviewStepsText ->
                saveLearningSteps(newStepsText, reviewStepsText)
            }
        )
    }

    private fun saveLearningSteps(newStepsText: String, reviewStepsText: String) {
        val request = LearningStepsSettingsPolicy.saveRequest(newStepsText, reviewStepsText)
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            return
        }
        SettingsWriteActions.saveLearningSteps(request, activity.store::saveLearningStepSettings)
        Toast.makeText(activity, SettingsTextCopy.learningStepsSavedToast(), Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }
}
