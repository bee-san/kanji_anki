package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.LearningStepsSettingsPolicy
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsLearningPanel(private val activity: MainActivitySettings) {
    fun learningStepsSettingsPanelModel(): SettingsLearningStepsPanelModel {
        return SettingsLearningStepsPanelModels.create(
            current = activity.store.learningStepSettings(),
            onSave = SettingsLearningStepsSaveAction { newStepsText, reviewStepsText ->
                saveLearningSteps(newStepsText, reviewStepsText)
            },
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
