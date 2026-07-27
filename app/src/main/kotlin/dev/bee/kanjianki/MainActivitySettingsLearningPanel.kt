package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.LearningStepsSettingsPolicy
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot

internal class MainActivitySettingsLearningPanel(private val activity: MainActivitySettings) {
    fun learningStepsSettingsPanelModel(
        snapshot: SettingsSnapshot = activity.loadSettingsSnapshot(),
    ): SettingsLearningStepsPanelModel {
        return SettingsLearningStepsPanelModels.create(
            current = snapshot.learningSteps,
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
        activity.runSettingsWrite(
            traceSection = "kani.settings.learning.save",
            write = {
                activity.saveSettings(
                    SettingsSaveCommand.LearningSteps(request.settings!!),
                )
            },
        ) {
            Toast.makeText(activity, SettingsTextCopy.learningStepsSavedToast(), Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }
}
