package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy

internal class MainActivitySettingsStudyAheadPanel(private val activity: MainActivitySettings) {
    fun studyAheadSettingsPanelModel(): SettingsStudyAheadPanelModel {
        return SettingsStudyAheadPanelModels.create(
            minutes = activity.store.studyAheadMinutes(),
            onSave = SettingsStudyAheadSaver { minutesText -> saveStudyAhead(minutesText) },
        )
    }

    private fun saveStudyAhead(minutesText: String) {
        val request = StudyAheadSettingsPolicy.saveRequest(minutesText)
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            return
        }
        activity.store.saveStudyAheadMinutes(request.minutes)
        Toast.makeText(activity, SettingsTextCopy.studyAheadSavedToast(), Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }
}
