package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot

internal class MainActivitySettingsStudyAheadPanel(private val activity: MainActivitySettings) {
    fun studyAheadSettingsPanelModel(
        snapshot: SettingsSnapshot = activity.loadSettingsSnapshot(),
    ): SettingsStudyAheadPanelModel {
        return SettingsStudyAheadPanelModels.create(
            minutes = snapshot.studyAheadMinutes,
            onSave = SettingsStudyAheadSaver { minutesText -> saveStudyAhead(minutesText) },
        )
    }

    private fun saveStudyAhead(minutesText: String) {
        val request = StudyAheadSettingsPolicy.saveRequest(minutesText)
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            return
        }
        activity.runSettingsWrite(
            traceSection = "kani.settings.study-ahead.save",
            write = {
                activity.saveSettings(SettingsSaveCommand.StudyAhead(request.minutes))
            },
        ) {
            Toast.makeText(activity, SettingsTextCopy.studyAheadSavedToast(), Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }
}
